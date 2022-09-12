import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.event.*;
import java.util.*;

import com.google.gson.*;

public class Main {
    public static ArrayList<String> versionList = new ArrayList<>();
    public static ArrayList<String> jsonLinks = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        getVersionList();
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        JFrame frame = new JFrame("majnkraft xdddd");
        frame.setLocation(100, 100);
        frame.getContentPane().setPreferredSize(new Dimension(5 + 128 + 5 + 128 + 5, 5 + 24 + 5 + 24 + 5 + 32 + 5));
        frame.pack();
        frame.setDefaultCloseOperation(3);
        frame.setResizable(false);
        JLabel versionLabel = new JLabel("Version");
        JLabel usernameLabel = new JLabel("Username");
        JComboBox versions = new JComboBox();
        for (String versionID : versionList) {
            versions.addItem(versionID);
        }
        JTextField username = new JTextField();
        JButton launch = new JButton("Launch");
        versionLabel.setBounds(5, 5, 128, 24);
        usernameLabel.setBounds(5, 34, 128, 24);
        versions.setBounds(138, 5, 128, 24);
        username.setBounds(138, 34, 128, 24);
        launch.setBounds(5, 63, 261, 32);
        launch.addActionListener(event -> {
            try {
                launch(username.getText(), versions.getSelectedIndex());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        frame.add(versionLabel);
        frame.add(usernameLabel);
        frame.add(versions);
        frame.add(username);
        frame.add(launch);
        frame.setLayout(null);
        frame.setVisible(true);
    }
    public static byte[] download(String url) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = new URL(url).openStream();
        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        while ((bytesRead = in.read(buffer)) > 0) {
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }
    public static void getVersionList() throws Exception {
        String versionManifestData = new String(download("https://piston-meta.mojang.com/mc/game/version_manifest.json"));
        JsonObject versionManifest = new JsonParser().parse(versionManifestData).getAsJsonObject();
        JsonArray versions = versionManifest.get("versions").getAsJsonArray();
        for (JsonElement element : versions) {
            JsonObject versionData = element.getAsJsonObject();
            versionList.add(versionData.get("id").getAsString());
            jsonLinks.add(versionData.get("url").getAsString());
        }
    }
    public static void launch(String username, int index) throws Exception {
        System.out.println("Launching version " + versionList.get(index) + "...");
        String versionData = new String(download(jsonLinks.get(index)));
        JsonObject root = new JsonParser().parse(versionData).getAsJsonObject();
        String assetIndex = root.get("assetIndex").getAsJsonObject().get("id").getAsString();
        new File("mc").mkdir();
        new File("mc/versions").mkdir();
        new File("mc/libraries").mkdir();
        new File("mc/assets").mkdir();
        new File("mc/assets/index").mkdir();
        new File("mc/assets/objects").mkdir();
        File client = new File("mc/versions/" + versionList.get(index) + ".jar");
        File indexFile = new File("mc/assets/index/" + assetIndex + ".json");
        File libraries = new File("mc/libraries");
        libraries.mkdir();
        if (!client.exists()) {
            System.out.println("Downloading client...");
            byte[] clientData = download(root.get("downloads").getAsJsonObject().get("client").getAsJsonObject().get("url").getAsString());
            OutputStream out = new FileOutputStream(new File("mc/versions/" + versionList.get(index) + ".jar"));
            out.write(clientData);
            out.close();
        }
        System.out.println("Downloading assets...");
        String assetIndexJsonData;
        if (!indexFile.exists()) {
            System.out.println("Downloading asset index...");
            byte[] assetIndexData = download(root.get("assetIndex").getAsJsonObject().get("url").getAsString());
            OutputStream out = new FileOutputStream(indexFile);
            out.write(assetIndexData);
            out.close();
            assetIndexJsonData = new String(assetIndexData);
        }
        else {
            InputStream in = new FileInputStream(indexFile);
            byte[] assetIndexData = new byte[in.available()];
            in.read(assetIndexData);
            in.close();
            assetIndexJsonData = new String(assetIndexData);
        }
        JsonObject assetIndexJson = new JsonParser().parse(assetIndexJsonData).getAsJsonObject().get("objects").getAsJsonObject();
        for (String key : assetIndexJson.keySet()) {
            JsonObject assetJson = assetIndexJson.get(key).getAsJsonObject();
            String hash = assetJson.get("hash").getAsString();
            String firstTwo = hash.substring(0, 2);
            File assetFile = new File("mc/assets/objects/" + firstTwo + "/" + hash);
            if (!assetFile.exists()) {
                System.out.println("Downloading asset " + key + "...");
                assetFile.getParentFile().mkdirs();
                OutputStream out = new FileOutputStream(assetFile);
                out.write(download("http://resources.download.minecraft.net/" + firstTwo + "/" + hash));
                out.close();
            }
        }
        System.out.println("Downloading libraries...");
        for (JsonElement libraryElement : root.get("libraries").getAsJsonArray()) {
            JsonObject libraryJson = libraryElement.getAsJsonObject().get("downloads").getAsJsonObject().get("artifact").getAsJsonObject();
            String path = libraryJson.get("path").getAsString();
            File libraryFile = new File(libraries, path);
            if (!libraryFile.exists()) {
                System.out.println("Downloading library " + path + "...");
                libraryFile.getParentFile().mkdirs();
                OutputStream out = new FileOutputStream(libraryFile);
                out.write(download(libraryJson.get("url").getAsString()));
                out.close();
            }
        }
        System.out.println("Starting Minecraft...");
        String[] args = new String[] {
            "java/bin/java.exe",
            "-cp",
            root.get("mainClass").getAsString(),
            "mc/versions/" + versionList.get(index) + ".jar",
            "--username",
            username,
            "--gameDir",
            "./mc",
            "--assetsDir",
            "./mc/assets",
            "--assetIndex",
            assetIndex,
            "--accessToken",
            "0"
        };
        Process minecraft = new ProcessBuilder().command(args).start();
        InputStream in = minecraft.getInputStream();
        Thread inThread = new Thread(() -> {
            try {
                while (true) {
                    while (in.available() > 0) {
                        System.out.print((char)in.read());
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        inThread.start();
        minecraft.waitFor();
        inThread.interrupt();
        System.out.println("Minecraft ended");
    }
}
