package utilities;

public class appTask {
    private String fileName;
    private boolean terminate;
    private long randomID;
    private int n;

    public int amount = 0;


    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isTerminate() {
        return terminate;
    }
    public void setTerminate(boolean terminate) {
        this.terminate = terminate;
    }

    public long getRandomID() {
        return randomID;
    }
    public void setRandomID(long randomID) {
        this.randomID = randomID;
    }

    public int getN() {
        return n;
    }
    public void setN(int n) {
        this.n = n;
    }
     @Override
    public String toString() {
        return "appTask [fileName=" + fileName + ", terminate=" + terminate + ",randomID:" + randomID + ",n:" + n + "]";
     }
}