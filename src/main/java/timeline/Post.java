package timeline;

import security.PostSignature;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

public class Post implements Serializable {
    private int id;
    private Date data;
    private String mensagem;
    private String assinatura; //TROCAR
    private String utilizador;

    public Post(int id, Date data, String mensagem, String assinatura, String utilizador) {
        this.id= id;
        this.data = data;
        this.mensagem = mensagem;
        this.assinatura = assinatura;
        this.utilizador = utilizador;
    }



    public Post(int id, String mensagem, String assinatura, String utilizador) {
        this.id = id;
        this.data = new Date();
        this.mensagem = mensagem;
        this.assinatura = assinatura;
        this.utilizador = utilizador;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getData() {
        return data;
    }

    public void setData(Date data) {
        this.data = data;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getAssinatura() {
        return assinatura;
    }

    public void setAssinatura(String assinatura) {
        this.assinatura = assinatura;
    }

    public String getUtilizador() {
        return utilizador;
    }

    public void setUtilizador(String utilizador) {
        this.utilizador = utilizador;
    }

    public byte[] computeHash () {
        return String.format( "%s-%d-%s-%s", this.id, this.data.getTime(), this.getUtilizador(), this.getMensagem() ).getBytes();
    }

    public String computeSignature ( PrivateKey privateKey ) throws Exception {
        byte[] hash = this.computeHash();

        return new String( new PostSignature().sign( hash, privateKey ) );
    }

    public boolean verify ( PublicKey publicKey ) throws Exception {
        return new PostSignature().verify( this.computeHash(), this.assinatura.getBytes(), publicKey );
    }

    public static Post createSigned ( int id, String message, String user, PrivateKey privateKey ) throws Exception {
        Post post = new Post( id, message, null, user );

        post.setAssinatura( post.computeSignature( privateKey ) );

        return post;
    }
}
