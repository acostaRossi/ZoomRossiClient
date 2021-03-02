package itisrossi;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.image.Image;

public class Client {

    public String ip;
    public int port;
    public ObjectProperty<Image> imageReceivedProperty;
    public Label lblUserName;

    public Client(String ip, int port) {
        this.ip = ip;
        this.port = port;
        imageReceivedProperty = new SimpleObjectProperty<Image>();
    }
}
