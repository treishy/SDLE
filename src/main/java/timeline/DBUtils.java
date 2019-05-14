package timeline;

import com.mongodb.Block;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;

public class DBUtils {
    MongoClient mongoClient;
    MongoDatabase db;
    MongoCollection<Document> collectionUsers;
    MongoCollection<Document> collectionPosts;
    //possivelmente dht
    public DBUtils(String username) {
        String postsCollection = String.format("Posts_%s", username);
        String peersCollection = String.format("Peers_%s",username);
        try {
            this.mongoClient = new MongoClient("localhost", 27017);
            this.db = mongoClient.getDatabase("P2P");
            if (db.getCollection(postsCollection) == null)
                db.createCollection(postsCollection);
            if (db.getCollection(peersCollection) == null)
                db.createCollection(peersCollection);
            this.collectionUsers = db.getCollection(peersCollection);
            this.collectionPosts = db.getCollection(postsCollection);
            cleanOldPosts();
        }
        catch (Exception e){
            e.printStackTrace();
        }

    }

    public void cleanOldPosts(){
        Date now = new Date();
        long weekago = now.getTime() - 604800000;
        DeleteResult result = collectionPosts.deleteMany(lt("getTime", weekago));
    }
    public boolean insertPost(Post post){
        boolean result = false;
        Document doc = new Document("id", post.getId())
                .append("mensagem", post.getMensagem())
                .append("utilizador", post.getUtilizador())
                .append("assinatura", post.getAssinatura())
                .append("data", post.getData())
                .append("getTime", post.getData().getTime());
        collectionPosts.insertOne(doc);
        if (collectionPosts.find(and(eq("id",post.getId()), eq("utilizador", post.getUtilizador()))).first()!= null)
            result = true;
        return result;
    }

    public boolean insertSubscription(User user){
        boolean result= false;
        Document doc = new Document("username", user.getUsername())
                .append("mensagem", user.getPassword())
                .append("atividade", user.getActivity());
        collectionUsers.insertOne(doc);
        if (collectionUsers.find(eq("username",user.getUsername())).first()!= null)
            result = true;
        return result;
    }

    public void updateUser(String username, int atividade){
        collectionUsers.updateOne(eq("username", username), new Document("$set", new Document("atividade", atividade)));
    }

    public void deleteAllPostsFromUser(String username){
        DeleteResult result = collectionPosts.deleteMany(eq("utilizador", username));
    }

    public void deleteSubscription(String username){
        collectionUsers.deleteOne(eq("username", username));
    }

    public List<User> findSubscriptions () {
        List<User> list = new ArrayList<>();

        collectionUsers.find().forEach( ( Consumer<? super Document> ) doc -> {
            String username = doc.getString( "username" );
            String password = doc.getString( "mensagem" );
            int activity = doc.getInteger( "atividade" );

            User user = new User( username, password, activity );

            list.add( user );
        } );

        return list;
    }

    public List<Post> findPosts ( String username ) {
        List<Post> list = new ArrayList<>();

        collectionPosts.find( eq( "utilizador", username ) ).forEach( ( Consumer<? super Document> ) doc -> {
            int id = doc.getInteger( "id" );
            Date date = doc.getDate( "data" );
            String message = doc.getString( "mensagem" );
            String signature = doc.getString( "assinatura" );

            Post post = new Post( id, date, message, signature, username );

            list.add( post );
        } );

        return list;
    }

    public static void main(String[] args) {
        DBUtils db = new DBUtils("teste");
        boolean result = false;
        User user = new User("joaquim", "melone");
        db.insertSubscription(new User("treishy", "melone"));
        result = db.insertPost(new Post(2, new Date(), "melane", "assinatura",user.getUsername()));
        System.out.println("Insert de User: "+ result);
    }
}
