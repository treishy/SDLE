package timeline;

import com.mongodb.BasicDBObject;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Application {
    public static boolean promptYesNo ( Scanner scanner ) {
        return promptYesNo( scanner, null );
    }

    public static boolean promptYesNo ( Scanner scanner, String label ) {
        if ( label != null ) {
            System.out.printf( "%s [Yes/No]: ", label );
        }

        String line = scanner.nextLine().toLowerCase();

        if ( line.equals( "yes" ) || line.equals( "y" ) ) {
            return true;
        } else if ( line.equals( "no" ) || line.equals( "n" ) ) {
            return false;
        } else {
            System.err.println( "Invalid input. Accepts yes, y, no, n." );

            return promptYesNo( scanner, label );
        }
    }

    public static String login ( Scanner scanner ) {
        System.out.println( "Username: " );

        return scanner.nextLine().trim();
    }

    // ARGS:
    // 1234
    // 2345 127.0.0.1 1234
    public static void commands ( Scanner scanner, TimelinePeer peer ) {
        String command = null;

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
                } else if ( command.startsWith( "sub add " ) ) {
                    String username = command.split( " " )[ 2 ];

                    if ( peer.username.equals( username ) ) {
                        System.err.println( "Can't subscribe to one's self.\n" );
                    } else if ( peer.isSubscribedTo( username ) ) {
                        System.err.println( "Already subscribed to " + username );
                    } else {
                        User user = peer.findProfile( username );

                        if ( user == null ) {
                            System.err.println( "Could not find user name " + username );
                        } else {
                            System.out.println( "Confirm user's fingerprint:" );
                            System.out.println( user.getPublicKeyFingerprint( peer.keys.getAlgorithm() ) );

                            if ( promptYesNo( scanner, "Valid fingerprint" ) ) {
                                peer.subscribeTo( user );

                                System.out.println( "Subscription complete." );
                            } else {
                                System.err.println( "Subscription cancelled." );
                            }
                        }
                    }
                } else if ( command.equals( "sub list" ) ) {
                    System.out.println( "SUBSCRIPTIONS:" );

                    for ( User sub : peer.subscriptions ) {
                        System.out.printf( " - %s (%d) \n", sub.getUsername(), sub.getActivity() );
                    }
                } else if ( command.startsWith( "sub remove" ) ) {
                    // TODO
                } else if ( command.startsWith( "sub update" ) ) {
                    String[] parts = command.split( " " );

                    if ( parts.length > 2 ) {
                        if ( peer.isSubscribedTo( parts[ 2 ] ) ) {
                            peer.update( parts[ 2 ] );
                            System.out.println( "Subscription updated." );
                        } else {
                            System.err.println( "Cannot update user " + parts[ 2 ] + ". Not subscribed to." );
                        }
                    } else {
                        for ( User sub : peer.subscriptions ) {
                            peer.update( sub.getUsername() );
                        }

                        System.out.println( "All subscriptions updated." );
                    }
                } else if ( command.equals( "help" ) ) {
                    System.out.println( "Available commands:" );
                    System.out.println( " - post <message>: Posts a message under the current user" );
                    System.out.println( " - find <username> <n>: Shows \"n\" newest messages from \"username\"" );
                    System.out.println( " - sub add <username>: Creates a subscription to a user" );
                    System.out.println( " - sub list: Lists all user subscriptions" );
                    System.out.println( " - sub update <username>: Refreshes all posts from the given username" );
                    System.out.println( " - sub update: Refreshes all posts from all subscriptions" );
                    System.out.println( " - help: Display this help message" );
                    System.out.println( " - quit: Gracefully closes the application" );
                } else if ( command.equals( "quit" ) ) {
                    System.out.println( "Exiting..." );
                } else if ( command.equals( "reset" ) ) {
                    peer.db.collectionUsers.deleteMany( new BasicDBObject() );
                    peer.db.collectionPosts.deleteMany( new BasicDBObject() );

                    break;
                } else {
                    System.out.println( "Unknown command. Try again." );
                }
            } catch ( Exception ex ) {
                ex.printStackTrace();
            }
        } while ( !command.equals( "quit" ) );
    }

    public static void main ( String[] args ) {
        Scanner scanner = new Scanner( System.in );

        String username = Application.login( scanner );

        TimelinePeer peer = new TimelinePeer( username );

        try {
            peer.load();

            InetSocketAddress address = args.length >= 3
                    ? InetSocketAddress.createUnresolved( args[ 1 ], Integer.parseInt( args[ 2 ] ) )
                    : null;

            peer.start( Integer.parseInt( args[ 0 ] ), address );

            System.out.printf( "Fingerprint:\n%s\n", peer.getUser().getPublicKeyFingerprint( peer.keys.getAlgorithm() ) );

            Application.commands( scanner, peer );

            peer.stop();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}
