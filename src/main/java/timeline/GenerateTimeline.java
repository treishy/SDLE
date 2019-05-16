package timeline;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

public class GenerateTimeline {
    protected static String header = "<html>\n" +
            "<head>\n" +
            "    <title>Timeline</title>\n" +
            "\n" +
            "    <link href=\"https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css\" rel=\"stylesheet\" integrity=\"sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T\" crossorigin=\"anonymous\">\n" +
            "\n" +
            "    <style>\n" +
            "        h1 {\n" +
            "            margin: 30px 0;\n" +
            "        }\n" +
            "\n" +
            "        .post .date {" +
            "           float: right;" +
            "        }" +
            "        .post .id {\n" +
            "            color: #CCCCCC;\n" +
            "        }\n" +
            "\n" +
            "        .post {\n" +
            "            border-top: 1px solid #EEEEEE;\n" +
            "            padding: 10px 20px;\n" +
            "        }\n" +
            "\n" +
            "        .post:last-child {\n" +
            "            border-bottom: 1px solid #EEEEEE;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1>Timeline</h1>";

    protected List<Post> posts;

    public GenerateTimeline ( List<Post> posts) {
        this.posts = posts;

        Collections.reverse( this.posts );
    }

    public void generate ( String file ) {
        try {
            FileWriter writer = new FileWriter( file );

            SimpleDateFormat dt = new SimpleDateFormat("dd MMM yyyy hh:mm");

            writer.write( header );

            int i = 1;

            for (Post post : this.posts) {
                writer.write( String.format( "<div class=\"post\">\n" +
                        "            <h4> <span class=\"id\">%d</span> %s <small class=\"date\">%s</small></h4>\n" +
                        "            <p>%s</p>\n" +
                        "        </div>", i, post.getUtilizador(), dt.format( post.getData() ), post.getMensagem() ) );

                i++;
            }
            writer.write( "</body></html>" );

            writer.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    public void generateAndOpen ( String file ) {
        this.generate( file );

        File htmlFile = new File(file);
        try {
            Desktop.getDesktop().browse(htmlFile.toURI());
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }
}
