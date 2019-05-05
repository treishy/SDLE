package timeline;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

import java.util.Date;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class DBUtils {
    MongoClient mongoClient;
    MongoDatabase db;
    MongoCollection<Document> collectionUsers;
    MongoCollection<Document> collectionPosts;
    //possivelmente dht
    public DBUtils() {
        try {
            this.mongoClient = new MongoClient("localhost", 27017);
            this.db = mongoClient.getDatabase("P2P");
            if (db.getCollection("Posts") == null)
                db.createCollection("Posts");
            if (db.getCollection("Peers") == null)
                db.createCollection("Peers");
            this.collectionUsers = db.getCollection("Peers");
            this.collectionPosts = db.getCollection("Posts");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public boolean insertPost(Post post){
        boolean result = false;
        Document doc = new Document("id", post.getId())
                .append("mensagem", post.getMensagem())
                .append("utilizador", post.getUtilizador())
                .append("assinatura", post.getAssinatura())
                .append("data", post.getData());
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

    public void deleteAllPostsFromUser(String username){
        DeleteResult result = collectionPosts.deleteMany(eq("utilizador", username));
        System.out.println(result.getDeletedCount());
    }

    public void deleteSubscription(String username){
        collectionUsers.deleteOne(eq("utilizador", username));
    }

    public static void main(String[] args) {
        DBUtils db = new DBUtils();
        boolean result = false;
        User user = new User("joaquim", "melone");
        //result = db.insertSubscription(new User("treishy", "melone"));
        result = db.insertPost(new Post(2, new Date(), "melane", "assinatura",user.getUsername()));
        System.out.println("Insert de User: "+ result);
    }


}
