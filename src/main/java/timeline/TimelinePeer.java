package timeline;

import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;

import java.io.IOException;
import java.net.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.PrivateKey;
import java.util.*;
import java.util.stream.Collectors;

public class TimelinePeer  {
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

    protected InetAddress address;

    protected Peer peer;

    protected PeerDHT peerDHT;

    protected List<User> subscriptions = new ArrayList<>();

    protected List<Post> posts = new ArrayList<>();

    protected TimelineServer server;

    protected DBUtils db;

    protected PeerKeys keys = new PeerKeys();

    protected int id = 0;

    public TimelinePeer ( String username ) {
        this.username = username;
    }

    public void startServer () {
        try {
            this.server = new TimelineServer( this);
            TimelineServerInterface stub = ( TimelineServerInterface ) UnicastRemoteObject.exportObject( server, 0 );

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.createRegistry(this.port + 1);
            registry.bind( "TimelineServerInterface", stub );

            System.err.println( "Server ready" );
        } catch ( Exception e ) {
            System.err.println( "Server exception: " + e.toString() );
            e.printStackTrace();
        }
    }

    public TimelineServerInterface createClient ( InetSocketAddress host ) {
        try {
            Registry registry = LocateRegistry.getRegistry( host.getHostString());

            TimelineServerInterface stub = ( TimelineServerInterface ) registry.lookup( "TimelineServerInterface" );

            return stub;
        } catch ( Exception e ) {
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Called before `start`, loads data from the database into the class (such as this user's own posts)
     */

    public void load () throws Exception {
        this.keys.init();

        this.db = new DBUtils();

        this.posts = this.db.findPosts( this.username );

        this.id = this.posts.stream().mapToInt( Post::getId ).max().orElse( -1 ) + 1;

        // TODO Load subscriptions
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

        InetSocketAddress portAddress = InetSocketAddress.createUnresolved( this.address.toString(), port );

        Number160 key = Number160.createHash( portAddress.toString() );

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
        }

        this.startServer();

        // TODO Self publish and publish all already existing subscriptions
        this.publishOwnership( this.username );
    }

    public void stop () {
        Number160 self = Number160.createHash( this.address.toString() );

        for ( User user : this.subscriptions ) {
            // TODO We can fork and join this futures, running them all at the same time
            this.peerDHT.remove( Number160.createHash( user.getUsername() ) ).contentKey( self ).start().awaitUninterruptibly();
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
        } else {
            throw new Exception( "Could not store the post in the database." );
        }
    }

    public List<Post> fetch ( String username, InetSocketAddress address ) {
        TimelineServerInterface first = this.createClient( address );
        try {
            List<Post> posts = first.getPosts( username, null );

            // TODO Validate posts and exclude posts that do not match the signature
            // TODO Store validated posts in the database

            return posts;
        } catch ( RemoteException e ) {
            return new ArrayList<>();
        }
    }

    /**
     * Given a username, searches the DHT for all IP addresses that profess to store that user's data. Then samples a
     * group of those addresses and queries them for the user timeline, validating each message by their signature,
     * and merging all the timelines, removing duplicates
     *
     * @param username
     */
    public List<Post> fetch ( String username ) {
        Collection<String> keys = EasyDHT.list( peerDHT, username );
        System.out.println(keys);

        List<InetSocketAddress> addresses = keys
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

    protected void publishOwnership ( String user ) throws IOException {
//        Number160 self = Number160.createHash( this.address.toString() );

        EasyDHT.add( peerDHT, user, this.address.getHostAddress() + ":" + this.port );
    }

    protected void unpublishOwnership ( String user ) throws IOException {
        EasyDHT.remove( peerDHT, user, this.address.getHostAddress() + ":" + this.port );
    }

    /**
     * Register a subscription, and adds itself to the DHT node for that user
     *
     * @param user
     */
    public void subscribeTo ( User user ) throws IOException {
        this.publishOwnership( user.getUsername() );

        // TODO Save subscription to database
        // TODO Fetch user posts and store them

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

        // TODO Remove subscription to database
        // TODO Remove user posts from the database
    }
}

interface TimelineServerInterface extends Remote {
    List<Post> getPosts ( String user, Date time ) throws RemoteException;
}

class TimelineServer implements TimelineServerInterface {
    protected TimelinePeer peer;

    public TimelineServer ( TimelinePeer peer ) throws RemoteException {
        this.peer = peer;
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
}
