import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.event.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import com.google.gson.*;

public class Main {
    public static ArrayList<String> versionList = new ArrayList<>();
    public static ArrayList<String> jsonLinks = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        if (!new File("java/bin/java").exists()) {
            System.out.print("Cannot find java installation on path './java'");
            return;
        }
        System.out.println("Fetching versions...");
        getVersionList();
        System.out.println("Done");
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
    public static byte[] download(String url, String name) throws Exception {
        System.out.print("Downloading " + name + "... 0%");
        byte[] data = download(url, percentage -> {
            System.out.print("\rDownloading " + name + "... " + percentage + "%");
        });
        System.out.println("\rDownloading " + name + "... 100%");
        return data;
    }
    public static byte[] download(String url) throws Exception {
        return readAllBytes(new URL(url).openStream());
    }
    public static byte[] download(String url, Progress progress) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        return readAllBytes(connection.getInputStream(), progress, connection.getContentLength());
    }
    public static byte[] readAllBytes(InputStream in) throws Exception {
        return readAllBytes(in, null, 0);
    }
    public static byte[] readAllBytes(InputStream in, Progress progress, int length) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        int totalBytesRead = 0;
        long time = System.currentTimeMillis();
        while ((bytesRead = in.read(buffer)) > 0) {
            out.write(buffer, 0, bytesRead);
            if (progress != null && System.currentTimeMillis() - time >= 500) {
                progress.progress(totalBytesRead * 100 / length);
                time = System.currentTimeMillis();
            }
            totalBytesRead += bytesRead;
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
        String versionData = new String(download(jsonLinks.get(index), "version metadata"));
        JsonObject root = new JsonParser().parse(versionData).getAsJsonObject();
        String assetIndex = root.get("assetIndex").getAsJsonObject().get("id").getAsString();
        new File("mc").mkdir();
        new File("mc/versions").mkdir();
        new File("mc/libraries").mkdir();
        new File("mc/assets").mkdir();
        new File("mc/assets/indexes").mkdir();
        new File("mc/assets/objects").mkdir();
        File client = new File("mc/versions/" + versionList.get(index) + ".jar");
        File indexFile = new File("mc/assets/indexes/" + assetIndex + ".json");
        File libraries = new File("mc/libraries");
        File natives = new File("mc/natives/" + versionList.get(index));
        libraries.mkdir();
        natives.mkdirs();
        if (!client.exists()) {
            byte[] clientData = download(root.get("downloads").getAsJsonObject().get("client").getAsJsonObject().get("url").getAsString(), "client");
            OutputStream out = new FileOutputStream("mc/versions/" + versionList.get(index) + ".jar");
            out.write(clientData);
            out.close();
        }
        System.out.println("Downloading assets...");
        String assetIndexJsonData;
        if (!indexFile.exists()) {
            byte[] assetIndexData = download(root.get("assetIndex").getAsJsonObject().get("url").getAsString(), "asset index");
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
                assetFile.getParentFile().mkdirs();
                OutputStream out = new FileOutputStream(assetFile);
                out.write(download("http://resources.download.minecraft.net/" + firstTwo + "/" + hash, "asset " + key));
                out.close();
            }
        }
        System.out.println("Downloading libraries...");
        String classpath = "";
        for (JsonElement libraryElement : root.get("libraries").getAsJsonArray()) {
            JsonObject downloads = libraryElement.getAsJsonObject().get("downloads").getAsJsonObject();
            if (downloads.has("artifact")) {
                JsonObject libraryJson = downloads.get("artifact").getAsJsonObject();
                String path = libraryJson.get("path").getAsString();
                File libraryFile = new File(libraries, path);
                if (!libraryFile.exists()) {
                    libraryFile.getParentFile().mkdirs();
                    OutputStream out = new FileOutputStream(libraryFile);
                    out.write(download(libraryJson.get("url").getAsString(), "library " + path));
                    out.close();
                }
                classpath += libraryFile.getPath() + (System.getProperty("os.name").contains("Windows") ? ";" : ":");
            }
            if (downloads.has("classifiers")) {
                JsonObject classifiers = downloads.get("classifiers").getAsJsonObject();
                for (String key : classifiers.keySet()) {
                    JsonObject classifier = classifiers.get(key).getAsJsonObject();
                    byte[] nativeLibraryData = download(classifier.get("url").getAsString(), "native library " + classifier.get("path").getAsString());
                    ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(nativeLibraryData));
                    for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                        String name = entry.getName();
                        if (name.endsWith(".dll") || name.endsWith(".dylib") || name.endsWith(".so")) {
                            File file = new File(natives, name);
                            OutputStream out = new FileOutputStream(file);
                            out.write(readAllBytes(zipIn));
                            out.close();
                            zipIn.closeEntry();
                        }
                    }
                    zipIn.close();
                }
            }
        }
        classpath += "mc/versions/" + versionList.get(index) + ".jar";
        System.out.println("Starting Minecraft...");
        String[] args = new String[] {
                "java/bin/java",
                "-Djava.library.path=mc/natives/" + versionList.get(index),
                "-cp",
                classpath,
                root.get("mainClass").getAsString(),
                "--username",
                username,
                "--gameDir",
                "./mc",
                "--assetsDir",
                "./mc/assets",
                "--assetIndex",
                assetIndex,
                "--accessToken",
                "0",
                "--version",
                versionList.get(index),
        };
        Process minecraft = new ProcessBuilder().command(args).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectErrorStream(true).start();
        minecraft.waitFor();
        System.out.println("Minecraft ended");
    }
    public interface Progress {
        void progress(int percentage);
    }
}
