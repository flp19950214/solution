import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;


public class main2 {

    static ConcurrentHashMap<String, CopyOnWriteArraySet<Trade2>> hashMap = new ConcurrentHashMap();
    static int threadNum = Runtime.getRuntime().availableProcessors();


    static ThreadPoolExecutor threadPool =
            new ThreadPoolExecutor(2, 3, 10, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
    static ThreadPoolExecutor threadPool2 =
            new ThreadPoolExecutor(0, 2, 10, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
    static ThreadPoolExecutor threadPool3 =
            new ThreadPoolExecutor(0, 2, 10, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());


    static volatile double profit = 0;
    static final String DELIMITER = ",";

    static boolean isUseThreadPool3 = false;

    static Trade2Res trade2Res = new Trade2Res();
    static Trade2 tradeOpen = new Trade2();
    static Trade2 tradeClose = new Trade2();
    static int num = 5000;

    public static void setProfit(double profitTemp, Trade2 trade1, Trade2 trade2) {
        if (profitTemp > profit) {
            synchronized (main2.class) {
                profit = profitTemp;
                tradeOpen = trade1;
                tradeClose = trade2;
            }
        }
    }
    static long startTime = System.currentTimeMillis();
    public static void main(String[] args) {

        String path;
        if (args.length == 0) {
            path = "D:\\RaceFile\\0001.csv";
        } else {
            path = args[0];
        }
        readFile(path);
        System.out.println(System.currentTimeMillis()-startTime);
        for (HashMap.Entry<String, CopyOnWriteArraySet<Trade2>> item : hashMap.entrySet()) {
            threadPool2.execute(() -> {
                handleData(item.getValue());
            });
        }
        threadPool2.shutdown();
        while (!threadPool2.isTerminated()) {
        }
        if (isUseThreadPool3) {
            threadPool3.shutdown();
            while (!threadPool3.isTerminated()) {
            }
        } else {
            threadPool3.shutdownNow();
        }

        assembleRes();
        System.out.println(trade2Res.toString());
        System.out.println(System.currentTimeMillis()-startTime);
    }

    public static void readFile(String path) {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                String finalLine = line;
                threadPool.execute(() -> {
                    try {
                        handleReadData(finalLine);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPool.shutdown();
        if (!threadPool.isTerminated()) {
        }
    }

    public static void handleData(CopyOnWriteArraySet<Trade2> set) {
        ArrayList<Trade2> collect = new ArrayList<>(set);
        int startIndex = 0;
        int endIndex = 0;
        if (collect.size() > num) {
            isUseThreadPool3 = true;
            for (int i = startIndex; i < collect.size(); i = startIndex) {
                int finalStartIndex = startIndex;
                endIndex = startIndex + num;
                endIndex = endIndex > collect.size() ? collect.size() : endIndex;
                int finalEndIndex = endIndex;
                threadPool3.execute(() -> {
                    handleData(collect, finalStartIndex, finalEndIndex);
                });
                startIndex += num;
            }
        } else {
            handleData(collect, 0, collect.size());
        }
    }

    public static void handleData(ArrayList<Trade2> collect, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            Trade2 trade1 = collect.get(i);
            if (trade1 == null) continue;
            if (trade1.bidVolume == 0 || trade1.askVolume == 0) {
                continue;
            }
            for (int j = 0; j < collect.size(); j++) {
                Trade2 trade2 = collect.get(j);
                if (trade2.bidVolume == 0 || trade2.askVolume == 0) {
                    continue;
                }
                double profit1 = 0;
                if (trade2.bidPrice > trade1.askPrice) {
                    profit1 = (trade1.bidPrice - trade2.askPrice) * (Math.min(trade2.bidVolume, trade1.askVolume)) * trade1.volumeMultiple;
                }

                double profit2 = 0;
                if (trade1.bidPrice > trade2.askPrice) {
                    profit2 = (trade1.bidPrice - trade2.askPrice) * (Math.min(trade2.askVolume, trade1.bidVolume)) * trade1.volumeMultiple;
                }
                double profitTemp = Math.max(profit1, profit2);
                setProfit(profitTemp, trade1, trade2);
            }
        }

    }

    public static void assembleRes() {
        trade2Res = new Trade2Res();
        trade2Res.openTime = tradeOpen.updateTime;
        trade2Res.openMillisec = tradeOpen.updateMillisec;
        StringBuilder closeTime = new StringBuilder();
        closeTime.append(tradeClose.updateTime).append(".").append(tradeClose.updateMillisec);
        trade2Res.closeTime = tradeClose.updateTime;
        trade2Res.closeMillisec = tradeClose.updateMillisec;
        trade2Res.profit = profit;
        trade2Res.instrumentID = tradeOpen.instrumentID;
    }


    public static void handleReadData(String line) {
        String[] columns = line.split(DELIMITER);
        String instrumentID = columns[21];
        Trade2 trade = new Trade2(columns[19], columns[20],
                Double.parseDouble(columns[22]), Integer.parseInt(columns[23]), Double.parseDouble(columns[24]), Integer.parseInt(columns[25]),
                columns[21], Integer.parseInt(columns[45]));
        CopyOnWriteArraySet<Trade2> orDefault;
        if(hashMap.get(instrumentID) == null || hashMap.get(instrumentID).size()==0){
            orDefault = new CopyOnWriteArraySet<>();
        }else{
            orDefault =  hashMap.get(instrumentID);
        }
        orDefault.add(trade);
        hashMap.put(instrumentID, orDefault);
    }
}

class Trade2 implements Comparable<Trade2> {
    String updateTime;
    String updateMillisec;
    double bidPrice;
    int bidVolume;
    double askPrice;
    int askVolume;
    String instrumentID;
    int volumeMultiple;

    public Trade2() {

    }

    public Trade2(String updateTime, String updateMillisec, double bidPrice, int bidVolume, double askPrice, int askVolume, String instrumentID, int volumeMultiple) {
        this.updateTime = updateTime;
        this.updateMillisec = updateMillisec;
        this.bidPrice = bidPrice;
        this.bidVolume = bidVolume;
        this.askPrice = askPrice;
        this.askVolume = askVolume;
        this.instrumentID = instrumentID;
        this.volumeMultiple = volumeMultiple;
    }

    @Override
    public int compareTo(Trade2 o) {
        if (this.updateTime.compareTo(o.updateTime) > 0) {
            return 1;
        }
        if (this.updateTime.compareTo(o.updateTime) == 0 && this.updateMillisec.compareTo(o.updateMillisec) > 0) {
            return 1;
        }
        if (this.updateTime.compareTo(o.updateTime) == 0 && this.updateMillisec.compareTo(o.updateMillisec) == 0) {
            return 0;
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade2 trade2 = (Trade2) o;
        return Double.compare(trade2.bidPrice, bidPrice) == 0 &&
                bidVolume == trade2.bidVolume &&
                Double.compare(trade2.askPrice, askPrice) == 0 &&
                askVolume == trade2.askVolume &&
                volumeMultiple == trade2.volumeMultiple;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bidPrice, bidVolume, askPrice, askVolume, volumeMultiple);
    }
}

class Trade2Res {
    //open_time,close_time,instrumentID,profit
    String openTime;
    String openMillisec;
    String closeTime;
    String closeMillisec;
    String instrumentID;
    double profit;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(openTime).append(".").append(String.format("%-3s", openMillisec).replace(" ", "0")).append(",").append(closeTime).append(".").append(String.format("%-3s", closeMillisec).replace(" ", "0")).append(",")
                .append(instrumentID).append(",").append(String.format("%.2f", Math.round(profit * 100.0) / 100.0));
        return sb.toString();
    }
}

class MyArrayList{
    private Trade2[] array;
    private int size;
    public MyArrayList(){
        array=new Trade2[10];
        size=0;
    }
    public void add(Trade2 ele){
        if(size==array.length){
            expendArray();
        }
        //从小到大排序

        array[size++]=ele;
    }
    public Trade2 get(int index){
        if(index<0||index>size){
            throw new IndexOutOfBoundsException();
        }
        return array[index];
    }
    public void expendArray(){
        Trade2[] newArray = new Trade2[array.length * 2];
        System.arraycopy(array, 0, newArray, 0, size);
        array = newArray;
    }
}

class SortedUniqueLinkedList<T> {
    private LinkedList<Trade2> list;

    public SortedUniqueLinkedList() {
        list = new LinkedList<>();
    }

    public void add(Trade2 num) {
        // 使用二分搜索找到应该插入的位置
        int index = search(num);
        // 如果列表中已存在该元素，则不进行插入
        if (index < 0) {
            return;
        }
        list.add(index, num);
    }

    public int size(){
        return list.size();
    }
    public Trade2 get(int index){
        return list.get(index);
    }

    private int search(Trade2 num) {
        int left = 0;
        int right = list.size() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (list.get(mid).compareTo(num)==0) {
                return -1; // 找到了相同的元素，不需要插入
            } else if (list.get(mid).compareTo(num) > 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        return left; // 返回应该插入的位置
    }
}
