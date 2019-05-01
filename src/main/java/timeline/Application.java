package timeline;

//import de.cgrotz.kademlia.Kademlia;
//import de.cgrotz.kademlia.config.UdpListener;
//import de.cgrotz.kademlia.node.Node;
//import de.cgrotz.kademlia.node.Key;
//import de.cgrotz.kademlia.storage.InMemoryStorage;
import net.tomp2p.dht.*;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;

import java.io.IOException;
import java.net.InetAddress;


public class Application {
    public static void main (String[] args) throws IOException, InterruptedException, ClassNotFoundException {
        System.out.println( "udp://127.0.0.1:" + args[ 1 ] );

        // Inicia-se o peer local (a classe Peer é do TomP2P, tem o mesmo nome da nossa, depois se calhar
        // convém renomear a nossa xD)
        Peer peerRaw = new PeerBuilder( new Number160( Integer.parseInt( args[ 0 ] ) ) )
                .ports( Integer.parseInt( args[ 1 ] ) )
                .start();

        // Inicia a DHT
        PeerDHT peer = new PeerBuilderDHT( peerRaw ).storage( new StorageMemory() ).start();

        // O segundo a iniciar liga-se ao IP do primeiro através do método bootstrap
        if ( args.length > 2 ) {
            peerRaw.bootstrap()
                    .inetAddress( InetAddress.getByName( "127.0.0.1" ) )
                    .ports( Integer.parseInt( args[ 2 ] ) )
                    .start();

            // Espera-se um bocado para ele se conectar aos outros Peers e obter as informações.
            // Não sei se há maneira melhor de fazer isto sem ser esperar n segundos
            Thread.sleep( 4000 );

            FutureGet fg = peer.get(Number160.createHash("key")).start();

            System.out.println( "Got " + new String( (byte[])fg.await().data().object() ) );
        } else {
            peer.put( Number160.createHash("key") ).object( "Ola".getBytes() ).start();
        }


        //store object
//        FutureDHT<BaseFuture> fp = peer.put(Number160.createHash("key")).setObject("hello world").build();
//
//        fp.awaitUninterruptibly();
//
//        //get object
//        FutureDHT fg = peer.get(Number160.createHash("key")).build();
//
//        //or block
//        fg.awaitUninterruptibly();

        //send direct messages to a particular peer
        // peer.sendDirect().setPeerAddress(peer1).setObject(“test”).build();
        // Kademlia kad = new Kademlia( new Key( Integer.parseInt( args[ 0 ] ) ), "udp://127.0.0.1:" + args[ 1 ], new InMemoryStorage() );

        // if ( args.length > 2 ) {
        //     Node node = Node.builder().advertisedListener(
        //         new UdpListener( "udp://127.0.0.1:" + args[ 2 ] )
        //     ).build();

        //     kad.bootstrap( node );

        //     System.out.println( kad.get( Key.build( "12" ) ) );
        // } else {
        //     kad.put( Key.build( "12" ), "Hello World" );
        //     kad.put( Key.build( "23" ), "Hello World 2" );
        //     // System.out.println( kad.get( new Key( 5 ) ) );
        // }
    }
}
