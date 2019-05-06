package timeline;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class EasyDHT {
    public static ArrayList<String> list ( PeerDHT dht, String key ) {
        return list( dht, Number160.createHash( key ) );
    }

    @SuppressWarnings( "unchecked" )
    public static ArrayList<String> list ( PeerDHT dht, Number160 key ) {
        try {
            FutureGet future = dht.get( key ).start();

            future.awaitUninterruptibly();

            Data data = future.data();

            if ( data == null ) {
                return new ArrayList<>();
            }

            Object obj = data.object();

            ArrayList<String> arr;

            if ( obj != null ) {
                arr = ( ArrayList<String> ) obj;
//                arr = new ArrayList( Arrays.asList( ((String)obj).substring( 1, ((String)obj).length() - 2 ).split( "," ) ) );
            } else {
                arr = new ArrayList<>();
            }

            return arr;
        } catch ( Exception ex ) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void add ( PeerDHT dht, String key, String value ) throws IOException {
        add( dht, Number160.createHash( key ), value );
    }

    public static void add ( PeerDHT dht, Number160 key, String value ) throws IOException {
        ArrayList<String> arr = list( dht, key );

        if ( !arr.contains( value ) ) {
            arr.add( value );
        }

        dht.put( key ).object( arr ).start().awaitUninterruptibly();
    }

    public static void remove ( PeerDHT dht, String key, String value ) throws IOException {
        remove( dht, Number160.createHash( key ), value );
    }

    public static void remove ( PeerDHT dht, Number160 key, String value ) throws IOException {
        ArrayList<String> arr = list( dht, key );

        arr.remove( value );

        dht.put( key ).object( arr ).start().awaitUninterruptibly();
    }
}
