package utilities;

public class managerTask {
    private String url;
    private long randomID;
    private String output;

    public long getRandomID() {
        return randomID;
    }
    public void setRandomID(long randomID) {
        this.randomID = randomID;
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    public String getOutput() {
        return output;
    }
    public void setOutput(String output) {
        this.output = output;
    }

    @Override
    public String toString() {
        return "managerTask [url=" + url + ", randomID=" + randomID + ", output=" + output + "]";
    }
}