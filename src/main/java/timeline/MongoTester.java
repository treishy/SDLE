package timeline;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoTester {

    public static void main(String[] args) {

        MongoClient mongoClient = new MongoClient("localhost", 27017);
        MongoDatabase db = mongoClient.getDatabase("P2P");
        DB database = mongoClient.getDB("P2P");
        //boolean auth = db.authenticate("username", "pwd".toCharArray());
        //ArrayList<String> dbs = new ArrayList(mongoClient.listDatabaseNames());
        for (String dbName: mongoClient.listDatabaseNames())
            System.out.println(dbName);
        if (db.getCollection("Peer") == null)
            db.createCollection("Peer");
        for (String listCollectionName : db.listCollectionNames()) {
            System.out.println("Collection denominada " + listCollectionName);
        }
        MongoCollection<Document> collection = db.getCollection("Peer");
        BasicDBObject document = new BasicDBObject();
        document.put("name", "Shubham");
        document.put("company", "Baeldung");
        Document doc = new Document("name", "Peer")
                .append("type", "database");
        collection.insertOne(doc);

    }
}
