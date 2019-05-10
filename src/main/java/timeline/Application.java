package timeline;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Application {
    public static String login () {
        Scanner scanner = new Scanner( System.in );

        System.out.println( "Username: " );

        return scanner.nextLine().trim();
    }

    // ARGS:
    // 1234
    // 2345 127.0.0.1 1234
    public static void commands ( TimelinePeer peer ) {
        String command = null;

        Scanner scanner = new Scanner( System.in );

        do {
            System.out.print( "> " );
            command = scanner.nextLine().trim();

            try {
                if ( command.startsWith( "post " ) ) {
                    peer.publish( command.substring( "post ".length() ) );
                } else if ( command.startsWith( "find " ) ) {
                    String username = command.split( " " )[ 1 ];

                    int count = Integer.parseInt( command.split( " " )[ 2 ] );

                    List<Post> posts = peer.find( username );

                    Collections.reverse( posts );

                    posts = posts.stream().limit( count ).collect( Collectors.toList() );

                    for ( Post post : posts ) {
                        System.out.printf( "%d %s: %s - %s\n", post.getId(), post.getData().toString(), post.getMensagem(), post.getUtilizador() );
                    }
                } else if ( command.equals( "help" ) ) {
                    System.out.println( "Available commands:" );
                    System.out.println( " - post <message>: Posts a message under the current user" );
                    System.out.println( " - find <username> <n>: Shows \"n\" newest messages from \"username\"" );
                    System.out.println( " - help: Display this help message" );
                    System.out.println( " - quit: Gracefully closes the application" );
                } else if ( command.equals( "quit" ) ) {
                    System.out.println( "Exiting..." );
                } else {
                    System.out.println( "Unknown command. Try again." );
                }
            } catch ( Exception ex ) {
                ex.printStackTrace();
            }
        } while ( !command.equals( "quit" ) );
    }

    public static void main ( String[] args ) {
        String username = Application.login();

        TimelinePeer peer = new TimelinePeer( username );

        try {
            peer.load();

            InetSocketAddress address = args.length >= 3
                    ? InetSocketAddress.createUnresolved( args[ 1 ], Integer.parseInt( args[ 2 ] ) )
                    : null;

            peer.start( Integer.parseInt( args[ 0 ] ), address );
            //peer.startServer();
            Application.commands( peer );

            peer.stop();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}
