package timeline;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;

import java.io.IOException;
import java.net.*;
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

    protected static int PORT = 4050;

    protected String username;

    protected InetAddress address;

    protected Peer peer;

    protected PeerDHT peerDHT;

    protected List<String> subscriptions = new ArrayList<>();

    /**
     * Connects to a known peer, member of the DHT, and integrates itself into the cluster
     *
     * @param knownPeer
     * @throws Exception
     */
    public void start ( InetSocketAddress knownPeer ) throws Exception {
        this.address = getSelfAddress();

        if ( this.address == null ) {
            throw new Exception( "Cannot determine own address." );
        }

        System.out.println( this.address.toString() );

        Number160 key = Number160.createHash( this.address.toString() );

        this.peer = new PeerBuilder( key )
                .ports( PORT )
                .start();

        // Inicia a DHT
        this.peerDHT = new PeerBuilderDHT( this.peer ).storage( new StorageMemory() ).start();

        // O segundo a iniciar liga-se ao IP do primeiro através do método bootstrap
        if ( knownPeer != null ) {
            this.peer.bootstrap()
                    .inetAddress( knownPeer.getAddress() )
                    .ports( knownPeer.getPort() )
                    .start();

            // Espera-se um bocado para ele se conectar aos outros Peers e obter as informações.
            // Não sei se há maneira melhor de fazer isto sem ser esperar n segundos
            Thread.sleep( 4000 );
        }
    }

    public void stop () {
        Number160 self = Number160.createHash( this.address.toString() );

        for ( String subscription : this.subscriptions ) {
            // TODO We can fork and join this futures, running them all at the same time
            this.peerDHT.remove( Number160.createHash( subscription ) ).contentKey( self ).start().awaitUninterruptibly();
        }
    }

    /**
     * Given a username, searches the DHT for all IP addresses that profess to store that user's data. Then samples a
     * group of those addresses and queries them for the user timeline, validating each message by their signature,
     * and merging all the timelines, removing duplicates
     *
     * @param username
     */
    public void fetch ( String username ) {
        List<InetAddress> addresses = this.peerDHT.get( Number160.createHash( username ) ).keys()
                .stream()
                .map( hash -> {
                    try {
                        return InetAddress.getByName( hash.toString() );
                    }catch ( Exception ex ) {
                        return null;
                    }
                } )
                .filter( Objects::nonNull )
                .collect( Collectors.toList() );

        System.out.println( addresses );

        // TODO Sample a few of the returned addresses, and ask each one for the user's contents
    }

    /**
     * Register a subscription, and adds itself to the DHT node for that user
     *
     * @param user
     */
    public void subscribeTo ( String user ) {
        Number160 self = Number160.createHash( this.address.toString() );

        try {
            this.peerDHT.put( Number160.createHash( user ) ).keyObject( self, this.username ).start().awaitUninterruptibly();
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        this.subscriptions.add( user );
    }

    /**
     * Removes a subscription, and removes itself from the DHT node for that user
     *
     * @param subscription
     */
    public void unsubscribeTo ( String subscription ) {
        Number160 self = Number160.createHash( this.address.toString() );

        this.peerDHT.remove( Number160.createHash( subscription ) ).contentKey( self ).start().awaitUninterruptibly();

        this.subscriptions.remove( subscription );
    }
}
