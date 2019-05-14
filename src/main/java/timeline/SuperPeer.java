package timeline;

import net.tomp2p.connection.Bindings;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.replication.IndirectReplication;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class SuperPeer {
    protected static InetAddress getSelfAddress () {
        try {
            return InetAddress.getByName( "127.0.0.1" );
        } catch ( UnknownHostException e ) {
            return null;
        }
    }

    protected int port = 4050;

    protected InetAddress address;

    protected Peer peer;

    protected PeerDHT peerDHT;

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
                .bindings( new Bindings().addAddress( this.address ) )
                .start();

        // Inicia a DHT
        this.peerDHT = new PeerBuilderDHT( this.peer )
                .storage( new StorageMemory() ).start();

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
    }

    public void stop () {
        this.peerDHT.shutdown().awaitUninterruptibly();

        this.peer.shutdown().awaitUninterruptibly();
    }
}
