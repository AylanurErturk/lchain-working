package util;
import java.io.*;
import java.util.Properties;

public final class PropertyManager {

    private Properties properties;
    private String fileName;

    public PropertyManager(String fileName) {
        loadProperties(fileName);
        this.fileName = fileName;
    }

    public void saveProperties() throws IOException {
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        String newGamePropertiesFile = rootPath + fileName;
        properties.store(new FileWriter(newGamePropertiesFile), "store to properties file");

    }

    public void updateProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public void loadProperties(String fileName) {
        try (Reader input = new BufferedReader(new FileReader(fileName))) {

            properties = new Properties();

           
            // load a properties file
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String getProperty(String key, String def) {
        return properties.getProperty(key, def);
    }
}