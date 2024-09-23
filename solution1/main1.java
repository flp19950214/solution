import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class main1 {
    static ConcurrentHashMap<String, Trade> instruTimeGroupMap = new ConcurrentHashMap();
    static int threadNum = Runtime.getRuntime().availableProcessors();

    static ThreadPoolExecutor threadPool =
            new ThreadPoolExecutor(3, 3, 10, TimeUnit.MILLISECONDS, new LinkedBlockingDeque());
    static ThreadPoolExecutor threadPool2 =
            new ThreadPoolExecutor(0, 3, 10, TimeUnit.MILLISECONDS, new LinkedBlockingDeque());
    static final String MARK = "_$_";
    static final String DELIMITER = ",";
    static Trade result = new Trade();
    public static void main(String[] args) {
        String path;
        if(args.length==0){
            path = "D:\\RaceFile\\0001.csv";
        }else{
            path=args[0];
        }
        readFile( path);
        HashMap<String, TreeSet<Trade>> hashMap = new HashMap();
        for (HashMap.Entry<String, Trade> entry : instruTimeGroupMap.entrySet()) {
            if (entry == null) continue;
            String key = entry.getKey().substring(0, entry.getKey().indexOf(MARK));
            if (hashMap.containsKey(key) && hashMap.get(key) != null) {
                hashMap.get(key).add(entry.getValue());
            } else {
                TreeSet<Trade> tradeTreeSet = new TreeSet();
                tradeTreeSet.add(entry.getValue());
                hashMap.put(key, tradeTreeSet);
            }
        }

        hashMap.keySet().stream().forEach(m -> {
            threadPool2.execute(() -> {
                TreeSet<Trade> treeSet = hashMap.get(m);
                Trade maxTrade = treeSet.first();
                Trade preTrade = treeSet.first();
                for (Trade curTrade : treeSet) {
                    int volume = curTrade.volume - preTrade.volume;
                    preTrade = curTrade;
                    if (volume > maxTrade.volume) {
                        maxTrade = new Trade(curTrade.time, curTrade.updateTime, curTrade.updateMillisec, curTrade.volume, curTrade.exchangeID, curTrade.instrumentID);
                        maxTrade.volume = volume;
                    }
                }
                if (result.volume < maxTrade.volume) {
                    result = maxTrade;
                }
            });
        });
        threadPool2.shutdown();
        while (!threadPool2.isTerminated()){}
        System.out.println(result);
    }//58404 5333  12850


    public static void handleData(String line) {
        String[] columns = line.split(DELIMITER);
        String minute = columns[19].substring(0, 5);
        StringBuilder sb = new StringBuilder();
        sb.append(columns[21]).append(MARK).append(minute);
        String key = sb.toString();
        Trade trade = new Trade(columns[19], columns[20], Integer.parseInt(columns[10]), columns[44], columns[21]);
        if (instruTimeGroupMap.containsKey(key)) {
            Trade temp = instruTimeGroupMap.get(key);
            int cur = Integer.parseInt(trade.updateTime.substring(6)) + Integer.parseInt(trade.updateMillisec);
            int pre = Integer.parseInt(temp.updateTime.substring(6)) + Integer.parseInt(temp.updateMillisec);
            if (cur == pre) {
                if (trade.volume > temp.volume) {
                    instruTimeGroupMap.put(key, trade);
                }
            } else if (cur > pre) {
                instruTimeGroupMap.put(key, trade);
            }
        } else {
            instruTimeGroupMap.put(key, trade);
        }
    }

    public static void readFile(String path) {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {
            br.readLine();
            String line;
            while((line = br.readLine())!=null){
                if(line == null)continue;
                String finalLine = line;
                threadPool.execute(() -> {
                    try {
                        handleData(finalLine);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPool.shutdown();
        while (!threadPool.isTerminated()){}
    }


}

class Trade implements Comparable<Trade> {
    String time;
    String updateTime;
    String updateMillisec;
    int volume;
    String exchangeID;
    String instrumentID;
    public Trade(){

    }
    public Trade(String updateTime,int volume,String exchangeID,String instrumentID){
        this.time=updateTime.substring(0,5);
        this.volume=volume;
        this.exchangeID=exchangeID;
        this.instrumentID=instrumentID;
    }

    public Trade(String updateTime,String updateMillisec,int volume,String exchangeID,String instrumentID){
        this.time=updateTime.substring(0,5);
        this.updateTime = updateTime;
        this.updateMillisec=updateMillisec;
        this.volume=volume;
        this.exchangeID=exchangeID;
        this.instrumentID=instrumentID;
    }
    public Trade(String time,String updateTime,String updateMillisec,int volume,String exchangeID,String instrumentID){
        this.time=time;
        this.updateTime = updateTime;
        this.updateMillisec=updateMillisec;
        this.volume=volume;
        this.exchangeID=exchangeID;
        this.instrumentID=instrumentID;
    }
    @Override
    public int compareTo(Trade o) {
        return (this.time).compareTo(o.time);
    }

    @Override
    public String toString() {
        return this.time+","+this.exchangeID+","+this.volume+","+this.instrumentID;
    }
}

