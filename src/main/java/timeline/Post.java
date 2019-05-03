package timeline;

import java.util.Date;

public class Post {
    private Date data;
    private String mensagem;
    private String assinatura; //TROCAR
    private User utilizador;

    public Post(Date data, String mensagem, String assinatura, User utilizador) {
        this.data = data;
        this.mensagem = mensagem;
        this.assinatura = assinatura;
        this.utilizador = utilizador;
    }

    public Post(String mensagem, String assinatura, User utilizador) {
        this.data = new Date();
        this.mensagem = mensagem;
        this.assinatura = assinatura;
        this.utilizador = utilizador;
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

    public User getUtilizador() {
        return utilizador;
    }

    public void setUtilizador(User utilizador) {
        this.utilizador = utilizador;
    }

}
