package timeline;

import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import security.PostSignature;

import java.io.IOException;
import java.io.Serializable;
import java.net.*;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class TimelinePeer extends SuperPeer {
    protected String username;

    protected User user;

    protected List<User> subscriptions = new ArrayList<>();

    protected List<Post> posts = new ArrayList<>();

    protected TimelineServer server;

    protected DBUtils db;

    protected PeerKeys keys;

    protected int id = 0;

    public TimelinePeer ( String username ) {
        this.username = username;
        this.keys = new PeerKeys( "./identity-" + username );
    }

    public User getUser () {
        return this.user;
    }

    public void startServer () {
        this.server = new TimelineServer( this );
    }

    public TimelineClient createClient ( InetSocketAddress host ) {
        return new TimelineClient( peer, host );
    }

    public TimelineClient createClient ( String username ) {
        InetSocketAddress addresses = this.findRandomAddress( username );

        if ( addresses != null ) {
            return createClient( addresses );
        }

        return null;
    }

    /**
     * Called before `start`, loads data from the database into the class (such as this user's own posts)
     */

    public void load () throws Exception {
        this.keys.init();

        this.db = new DBUtils(this.username);

        this.posts = this.db.findPosts( this.username );

        this.id = this.posts.stream().mapToInt( Post::getId ).max().orElse( -1 ) + 1;

        this.user = new User( this.username, this.keys.publicKey.write(), this.posts.size() );

        this.subscriptions = this.db.findSubscriptions();
    }

    /**
     * Connects to a known peer, member of the DHT, and integrates itself into the cluster
     *
     * @param knownPeer
     * @throws Exception
     */
    public void start ( int port, InetSocketAddress knownPeer ) throws Exception {
        super.start( port, knownPeer );

        this.startServer();

        this.publishOwnership( this.username );

        for ( User user : this.subscriptions ) {
            try {
                this.update( user.getUsername() );
                this.update( user.getUsername() );

                this.publishOwnership( user.getUsername() );
            } catch ( Exception ex ) {
                ex.printStackTrace();
            }
        }
    }

    public void stop () {
        for ( User user : this.subscriptions ) {
            try {
                this.unpublishOwnership( user.getUsername() );
            } catch ( Exception ex ) {
                ex.printStackTrace();
            }
        }

        super.stop();

        this.db.mongoClient.close();
    }

    public void publish ( String message ) throws Exception {
        int id = this.id++;

        Post post = Post.createSigned( id, message, this.username, this.keys.privateKey.get() );

        if ( this.db.insertPost( post ) ) {
            this.posts.add( post );

            this.user.setActivity( this.user.getActivity() + 1 );
        } else {
            throw new Exception( "Could not store the post in the database." );
        }

        this.findAddresses( this.username )
                .stream()
                .filter( addr -> !addr.getHostString().equals( this.address.getHostAddress() ) || addr.getPort() != this.port )
                .limit( 100 )
                .forEach( addr -> {
                    TimelineClient client = createClient( addr );

                    client.pushPost( post );
                } );
    }

    public List<Post> fetch ( String username, InetSocketAddress address ) {
        TimelineServerInterface first = this.createClient( address );

        List<Post> posts = first.getPosts( username, null );

        User subscription = this.getSubscription( username );

        if ( subscription != null ) {
            PublicKey key = subscription.getPublicKey( this.keys.getAlgorithm() );

            if ( key != null ) {
                posts = posts.stream()
                        .filter( post -> {
                            try {
                                return post.verify( key );
                            } catch ( Exception e ) {
                                e.printStackTrace();

                                return false;
                            }
                        } )
                        .collect( Collectors.toList() );
            }
        }

        return posts;
    }

    public InetSocketAddress findRandomAddress ( List<InetSocketAddress> addresses ) {
        int i = ThreadLocalRandom.current().nextInt( addresses.size() );

        return addresses.get( i );
    }

    public InetSocketAddress findRandomAddress ( String username ) {
        return findRandomAddress( findAddresses( username ) );
    }

    public List<InetSocketAddress> findAddresses ( String username ) {
        Collection<String> keys = EasyDHT.list( peerDHT, username );

        return keys
                .stream()
                .map( hash -> {
                    try {
                        String[] parts = hash.split( ":" );

                        System.out.printf( "FOUND %s, %s\n", hash, username );

                        return InetSocketAddress.createUnresolved( parts[ 0 ], Integer.parseInt( parts[ 1 ] ) );
                    } catch ( Exception ex ) {
                        return null;
                    }
                } )
                .filter( Objects::nonNull )
                .collect( Collectors.toList() );
    }

    /**
     * Given a username, searches the DHT for all IP addresses that profess to store that user's data. Then samples a
     * group of those addresses and queries them for the user timeline, validating each message by their signature,
     * and merging all the timelines, removing duplicates
     *
     * @param username
     */
    public List<Post> fetch ( String username ) {
        InetSocketAddress addresses = this.findRandomAddress( username );

        // TODO Sample a few of the returned addresses, and ask each one for the user's contents
        if ( addresses != null ) {
            return fetch( username, addresses );
        }

        return new ArrayList<>();
    }

    public List<Post> find ( String username ) {
        if ( this.username.equals( username ) ) {
            return this.posts;
        } else if ( this.subscriptions.stream().anyMatch( user -> user.getUsername().equals( username ) ) ) {
            return this.db.findPosts( username );
        } else {
            return this.fetch( username );
        }
    }

    public User findProfile ( String username ) {
        TimelineClient client = this.createClient( username );

        if ( client == null ) {
            return null;
        }

        User user = client.getProfile( username );

        if ( user != null && user.getUsername() != null && user.getUsername().equals( username ) ) {
            return user;
        }

        return null;
    }

    public void update ( String username ) {
        User subscription = this.getSubscription( username );

        Set<Integer> existing = this.db.findPosts( username ).stream().map( Post::getId ).collect( Collectors.toSet() );

        List<Post> posts = this.fetch( username ).stream().filter( p -> !existing.contains( p.getId() ) ).collect( Collectors.toList() );

        posts.forEach( p -> this.db.insertPost( p ) );

        subscription.setActivity( subscription.getActivity() + posts.size() );

        this.db.updateUser( username, subscription.getActivity() );
    }

    protected void publishOwnership ( String user ) throws IOException {
        EasyDHT.add( peerDHT, user, this.address.getHostAddress() + ":" + this.port );
    }

    protected void unpublishOwnership ( String user ) throws IOException {
        EasyDHT.remove( peerDHT, user, this.address.getHostAddress() + ":" + this.port );
    }

    public User getSubscription ( String user ) {
        return this.subscriptions.stream().filter( u -> u.getUsername().equals( user ) ).findFirst().orElse( null );
    }

    public boolean isSubscribedTo ( String user ) {
        return this.subscriptions.stream().anyMatch( u -> u.getUsername().equals( user ) );
    }

    /**
     * Register a subscription, and adds itself to the DHT node for that user
     *
     * @param user
     */
    public void subscribeTo ( User user ) throws IOException {
        this.subscriptions.add( user );

        this.update( user.getUsername() );

        this.db.insertSubscription( user );

        this.publishOwnership( user.getUsername() );
    }

    /**
     * Removes a subscription, and removes itself from the DHT node for that user
     *
     * @param user
     */
    public void unsubscribeTo ( User user ) throws IOException {
        this.unpublishOwnership( user.getUsername() );

        this.subscriptions.remove( user );

        this.db.deleteAllPostsFromUser( user.getUsername() );

        this.db.deleteSubscription( user.getUsername() );
    }
}

interface TimelineServerInterface {
    List<Post> getPosts ( String user, Date time );

    void pushPost ( Post post );
}

class TimelineRequestMessage implements Serializable {
    public enum Type {GetPosts, GetProfile, PushPost}

    public Type type;


    // GetPosts
    public Date time;

    // GetProfile & GetPosts
    public String user;

    // PushPost
    public Post post;
}

class TimelineResponseMessage implements Serializable {
    // GetPosts
    public List<Post> posts;

    // GetProfile
    public User user;
}

class TimelineClient implements TimelineServerInterface {
    private Peer peer;
    private InetSocketAddress address;

    public TimelineClient ( Peer peer, InetSocketAddress address ) {
        this.peer = peer;
        this.address = address;
    }

    private TimelineResponseMessage call ( TimelineRequestMessage request ) {
        Number160 key = Number160.createHash( address.getHostString() + ":" + address.getPort() );

        try {
            FutureDirect fd = peer.sendDirect( new PeerAddress( key, address.getHostString(), address.getPort(), address.getPort() ) ).object( request ).start();

            fd.awaitUninterruptibly();

            if ( fd.isSuccess() ) {
                return ( TimelineResponseMessage ) fd.object();
            } else {
                System.err.println( fd.failedReason() );
                return null;
            }
        } catch ( ClassNotFoundException | IOException e ) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Post> getPosts ( String user, Date time ) {
        TimelineRequestMessage request = new TimelineRequestMessage();

        request.type = TimelineRequestMessage.Type.GetPosts;
        request.user = user;
        request.time = time;

        TimelineResponseMessage response = this.call( request );

        if ( response == null ) {
            return new ArrayList<>();
        }

        return response.posts;
    }

    public User getProfile ( String username ) {
        TimelineRequestMessage request = new TimelineRequestMessage();

        request.type = TimelineRequestMessage.Type.GetProfile;
        request.user = username;

        TimelineResponseMessage response = this.call( request );

        if ( response == null ) {
            return null;
        }

        return response.user;
    }

    public void pushPost ( Post post ) {
        TimelineRequestMessage request = new TimelineRequestMessage();

        request.type = TimelineRequestMessage.Type.PushPost;
        request.post = post;

        this.call( request );
    }
}

class TimelineServer implements TimelineServerInterface {
    protected TimelinePeer peer;

    public TimelineServer ( TimelinePeer peer ) {
        this.peer = peer;

        this.peer.peer.objectDataReply( ( PeerAddress sender, Object rawRequest ) -> {
            TimelineRequestMessage request = ( TimelineRequestMessage ) rawRequest;

            TimelineResponseMessage response = new TimelineResponseMessage();

            if ( request.type == TimelineRequestMessage.Type.GetPosts ) {
                response.posts = this.getPosts( request.user, request.time );
            } else if ( request.type == TimelineRequestMessage.Type.GetProfile ) {
                response.user = this.getProfile( request.user );
            } else if ( request.type == TimelineRequestMessage.Type.PushPost ) {
                this.pushPost( request.post );
            }

            return response;
        } );
    }

    public List<Post> getPosts ( String user, Date time ) {
        if ( this.peer.username.equals( user ) ) {
            return peer.posts
                    .stream()
                    .filter( post -> time == null || post.getData().after( time ) )
                    .collect( Collectors.toList() );
        } else {
            // When the requested username is not our own
            // Lookup in the Mongo database for posts from this user
            return this.peer.db.findPosts( user )
                    .stream()
                    .filter( post -> time == null || post.getData().after( time ) )
                    .collect( Collectors.toList() );
        }
    }

    public User getProfile ( String user ) {
        if ( this.peer.username.equals( user ) ) {
            return this.peer.getUser();
        } else {
            return this.peer.getSubscription( user );
        }
    }

    public void pushPost ( Post post ) {
        if ( !this.peer.isSubscribedTo( post.getUtilizador() ) ) {
            return;
        }

        User subscription = this.peer.getSubscription( post.getUtilizador() );

        try {
            if ( post.verify( subscription.getPublicKey( this.peer.keys.getAlgorithm() ) ) ) {
                boolean exists = this.peer.db.hasPost( post.getUtilizador(), post.getId() );

                if ( !exists ) {
                    this.peer.db.insertPost( post );

                    this.peer.db.updateUser( subscription.getUsername(), subscription.getActivity() + 1 );
                }
            }
        } catch ( Exception e ) { }
    }
}
