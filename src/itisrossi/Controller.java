package itisrossi;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;

import com.github.sarxos.webcam.Webcam;
import javafx.scene.layout.VBox;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;

public class Controller implements Initializable {

    @FXML
    public TextField txtUserName;

    @FXML
    public Label lblMyUserName;

    @FXML
    ComboBox<WebCamInfo> cbCameraOptions;

    @FXML
    FlowPane fpBottomPane;

    @FXML
    ImageView imgWebCamCapturedImage;

    @FXML
    private VBox vbox1;

    @FXML
    private VBox vbox2;

    @FXML
    private VBox vbox3;

    private ArrayList<Client> clients;
    private String userName = "utente";
    private BufferedImage grabbedImage;
    private Webcam selWebCam = null;
    private ObjectProperty<Image> imageProperty = new SimpleObjectProperty<Image>();
    private DatagramSocket clientSocketS = null;
    private InetAddress IPAddress = null;

    int frameCounter = 0;

    final int FRAME_SIZE = 400;

    //final String SERVER_IP = "139.59.138.199";
    final String SERVER_IP = "127.0.0.1";

    int currVBox = 1;

    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {

        try {
            clientSocketS = new DatagramSocket();
            IPAddress = InetAddress.getByName(SERVER_IP);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        clients = new ArrayList<>();

        fpBottomPane.setDisable(true);
        ObservableList<WebCamInfo> options = FXCollections.observableArrayList();
        int webCamCounter = 0;

        for (Webcam webcam : Webcam.getWebcams()) {
            WebCamInfo webCamInfo = new WebCamInfo();
            webCamInfo.setWebCamIndex(webCamCounter);
            webCamInfo.setWebCamName(webcam.getName());
            options.add(webCamInfo);
            webCamCounter++;
        }

        cbCameraOptions.setItems(options);
        cbCameraOptions.setPromptText("Scegli la Webcam");
        cbCameraOptions.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<WebCamInfo>() {

            @Override
            public void changed(ObservableValue<? extends WebCamInfo> arg0, WebCamInfo arg1, WebCamInfo arg2) {
                if (arg2 != null) {
                    initializeWebCam(arg2.getWebCamIndex());
                    initializeReceiver();
                    cbCameraOptions.setDisable(true);
                    //txtUserName.setDisable(true);
                }
            }
        });
    }

    public void txtUserNameChanged(KeyEvent keyEvent) {

        if(!txtUserName.getText().equals("")) {
            userName = txtUserName.getText();
            lblMyUserName.setText(userName);
        }
    }

    protected void initializeWebCam(final int webCamIndex) {

        selWebCam = Webcam.getWebcams().get(webCamIndex);
        selWebCam.open();

        startWebCamStream();
    }

    /*
    *   Retrieve image from webcam and send it to server
    */
    protected void startWebCamStream() {

        Runnable runnable = () -> {

            long startTime = System.currentTimeMillis();

            while(!Thread.interrupted()) {

                try {
                    long estimatedTime = System.currentTimeMillis() - startTime;

                    // every 200 ms

                    if(estimatedTime > 200) {

                        startTime = System.currentTimeMillis();

                        // get image from webcam

                        if ((grabbedImage = selWebCam.getImage()) != null) {

                            // transform image to byte

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try {
                                BufferedImage scaledImg = Scalr.resize(grabbedImage, 200, 150);
                                ImageIO.write(scaledImg, "jpg", baos);
                                baos.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            byte[] imageInByte = baos.toByteArray();

                            // break image in more than one parts

                            int parts = imageInByte.length / FRAME_SIZE;

                            if(imageInByte.length > parts * FRAME_SIZE) {
                                parts++;
                            }

                            for(int y=0; y<parts; y++) {

                                int length = FRAME_SIZE;

                                // length of last image part

                                if(y == parts-1) {

                                    if(imageInByte.length > (parts-1) * FRAME_SIZE) {

                                        length = imageInByte.length - (FRAME_SIZE * y);
                                    }
                                }

                                // prepare byte buffer

                                ByteBuffer bbuffer = ByteBuffer.allocate(length + (Integer.BYTES * 5) + 25);

                                // put USER NAME

                                bbuffer.put(userName.getBytes());

                                bbuffer.position(25);

                                // put FRAME NUMBER

                                bbuffer.putInt(frameCounter);

                                // put PART NUMBER

                                bbuffer.putInt(y);

                                // put TOTAL IMG SIZE

                                bbuffer.putInt(imageInByte.length);

                                // put PART SIZE

                                bbuffer.putInt(length);

                                // put PART TOTAL

                                bbuffer.putInt(parts);

                                // put PART

                                bbuffer.put(imageInByte, y * FRAME_SIZE, length);

                                // send packet

                                DatagramPacket packet = new DatagramPacket(bbuffer.array(), bbuffer.array().length, IPAddress, 8);

                                try {
                                    clientSocketS.send(packet);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            frameCounter++;

                            if(frameCounter > 5000) {
                                frameCounter = 0;
                            }

                            // update UI

                            Platform.runLater(() -> {

                                if(grabbedImage != null) {
                                    final Image mainimage = SwingFXUtils
                                            .toFXImage(grabbedImage, null);
                                    imageProperty.set(mainimage);
                                }
                            });

                            if(grabbedImage != null) {
                                grabbedImage.flush();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };

        Thread th = new Thread(runnable);
        th.setDaemon(true);
        th.start();
        imgWebCamCapturedImage.imageProperty().bind(imageProperty);
    }

    /*
     *   Receive datagram from server and show the image in UI
     */
    protected void initializeReceiver() {

        Runnable runnable = () -> {

            while(!Thread.interrupted()) {

                byte[] dtArray = new byte[FRAME_SIZE + (Integer.BYTES * 5) + 25];

                DatagramPacket dtPacket = new DatagramPacket(dtArray, dtArray.length);

                try {
                    clientSocketS.receive(dtPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                ByteBuffer bbuffer = ByteBuffer.wrap(dtPacket.getData());

                // USERNAME

                byte[] byteArrayUserName = new byte[25];

                bbuffer.get(byteArrayUserName);

                String nickname = new String(byteArrayUserName);

                bbuffer.position(25);

                // FRAME number

                int count = bbuffer.getInt();

                // PART number

                int index = bbuffer.getInt();

                // TOTAL IMG SIZE

                int totalImgSize = bbuffer.getInt();

                // PART SIZE

                int partSize = bbuffer.getInt();

                // PART TOTAL number

                int partsTotal = bbuffer.getInt();

                // estraggo la parte di frame dal datagram

                byte[] bArrayImgPart = new byte[partSize];

                bbuffer.get(bArrayImgPart);

                // identifico il client

                boolean isNewClient = true;

                int clientIndex = 0;

                for (Client client: clients) {
                    // se stesso ip
                    if(client.ip.equals(dtPacket.getAddress().getHostAddress())) {
                        // se stessa porta
                        if(client.port == dtPacket.getPort()) {
                            isNewClient = false;
                            break;
                        }
                    }
                    clientIndex++;
                }

                // if is new user connected add new ImageView in UI

                if(isNewClient) {

                    Client newClient = new Client(dtPacket.getAddress().getHostAddress(), dtPacket.getPort());
                    clients.add(newClient);

                    Platform.runLater(() -> {

                        VBox vbox = getVbox();
                        Label lblUser = new Label();
                        newClient.lblUserName = lblUser;
                        lblUser.setText(nickname);
                        vbox.getChildren().add(lblUser);
                        ImageView imageView = new ImageView();
                        imageView.setFitWidth(200);
                        imageView.setFitHeight(150);
                        imageView.setX(0);
                        imageView.setY(0);
                        vbox.getChildren().add(imageView);
                        imageView.imageProperty().bind(newClient.imageReceivedProperty);
                    });
                }

                // se è il primo pezzo di immagine

                if(index == 0) {

                    // inzializzo un nuovo byte buffer che conterrà l'immagine

                    clients.get(clientIndex).imgFinalByte = ByteBuffer.allocate(totalImgSize);
                }

                if(clients.get(clientIndex).imgFinalByte != null) {

                    // mi posiziono nella posizione corretta del byte buffer

                    clients.get(clientIndex).imgFinalByte.position(index * FRAME_SIZE);

                    // aggiungo al byte buffer il pezzo di immagine

                    try {
                        clients.get(clientIndex).imgFinalByte.put(bArrayImgPart);
                    } catch (BufferOverflowException e) {
                        e.printStackTrace();
                    }

                    // se è l'ultimo pezzo dell'immagine

                    if(index == partsTotal - 1) {

                        // controllo se l'immagine è completa

                        if(totalImgSize <= clients.get(clientIndex).imgFinalByte.array().length) {

                            // crea l'immagine a partire dal byte array

                            BufferedImage bImage = null;
                            try {
                                bImage = ImageIO.read(new ByteArrayInputStream(clients.get(clientIndex).imgFinalByte.array()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            if(bImage != null) {

                                // mostra l'immagine nella view corrispondente al client

                                Image mainimage = SwingFXUtils.toFXImage(bImage, null);

                                clients.get(clientIndex).imageReceivedProperty.set(mainimage);

                                if(clients.get(clientIndex).lblUserName != null) {
                                    int finalClientIndex = clientIndex;
                                    Platform.runLater(() -> {
                                        clients.get(finalClientIndex).lblUserName.setText(nickname);
                                    });
                                }

                            }
                        }

                        clients.get(clientIndex).imgFinalByte.clear();
                    }
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.start();
    }

    // get the vbox in order to add a user
    public VBox getVbox() {

        if(currVBox == 1) {
            currVBox = 2;
            return vbox2;
        }
        if(currVBox == 2) {
            currVBox = 3;
            return vbox3;
        }
        if(currVBox == 3) {
            currVBox = 1;
            return vbox1;
        }

        return vbox3;
    }
}