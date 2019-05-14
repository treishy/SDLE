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
import java.util.stream.Collectors;

public class TimelinePeer {
    protected static InetAddress getSelfAddress () {
        try ( final DatagramSocket socket = new DatagramSocket() ) {
            socket.connect( InetAddress.getByName( "8.8.8.8" ), 10002 );

            return InetAddress.getByName( socket.getLocalAddress().getHostAddress() );
        } catch ( Exception ex ) {
            return null;
        }
    }

    protected int port = 4050;

    protected String username;

    protected User user;

    protected InetAddress address;

    protected Peer peer;

    protected PeerDHT peerDHT;

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
        List<InetSocketAddress> addresses = this.findAddresses( username );

        if ( addresses.size() > 0 ) {
            return createClient( addresses.get( 0 ) );
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
        this.port = port;

        this.address = getSelfAddress();

        if ( this.address == null ) {
            throw new Exception( "Cannot determine own address." );
        }

        InetSocketAddress portAddress = InetSocketAddress.createUnresolved( this.address.getHostAddress(), port );

        Number160 key = Number160.createHash( portAddress.getHostString() + ":" + portAddress.getPort() );

        this.peer = new PeerBuilder( key )
                .ports( port )
                .start();

        // Inicia a DHT
        this.peerDHT = new PeerBuilderDHT( this.peer ).storage( new StorageMemory() ).start();

        // O segundo a iniciar liga-se ao IP do primeiro através do método bootstrap
        if ( knownPeer != null ) {
            this.peer.bootstrap()
                    .inetAddress( InetAddress.getByName( knownPeer.getHostName() ) )
                    .ports( knownPeer.getPort() )
                    .start();

            // Espera-se um bocado para ele se conectar aos outros Peers e obter as informações.
            // Não sei se há maneira melhor de fazer isto sem ser esperar n segundos
            Thread.sleep( 4000 );
        } else {
            this.startServer();
        }

        // TODO Self publish and publish all already existing subscriptions
        this.publishOwnership( this.username );

        for ( User user : this.subscriptions ) {
            try {
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

        this.peerDHT.shutdown().awaitUninterruptibly();

        this.peer.shutdown().awaitUninterruptibly();

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
        List<InetSocketAddress> addresses = this.findAddresses( username );

        // TODO Sample a few of the returned addresses, and ask each one for the user's contents
        if ( addresses.size() > 0 ) {
            return fetch( username, addresses.get( 0 ) );
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
        this.update( user.getUsername() );

        this.db.insertSubscription( user );

        this.publishOwnership( user.getUsername() );

        this.subscriptions.add( user );
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
}

class TimelineRequestMessage implements Serializable {
    public enum Type {GetPosts, GetProfile}

    public Type type;


    // GetPosts
    public Date time;

    // GetProfile & GetPosts
    public String user;
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
            // When the requested username is now our own
            // Lookup in the Mongo database for posts from this user
            return new ArrayList<>();
        }
    }

    public User getProfile ( String user ) {
        if ( this.peer.username.equals( user ) ) {
            return this.peer.getUser();
        } else {
            return this.peer.getSubscription( user );
        }
    }
}
