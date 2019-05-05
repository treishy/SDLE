package timeline;

import java.util.Date;

public class Post {
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

}
