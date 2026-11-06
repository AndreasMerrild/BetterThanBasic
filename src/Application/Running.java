package Application;

import Ftp.ftp;
import Fit.FitActivity;
import Fit.FitParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

public class Running {
        public void run() {
            Scanner scanner = new Scanner(System.in);
            boolean active = true;
            boolean first = true;
            while(active) {
                printOptions(first);
                if(first) {
                    first = false;
                }
                active = goInto(scanner, active);
            }
        }
        public void printOptions(boolean first){
            if(first == true) {
                System.out.println("---------------------------------------------------");
                System.out.println("Welcome to the power calculater. ");
                System.out.println("Please submit all numbers as doubles (XXX.XX)");
                System.out.println("Let's start by choosing what part you want.");
            }
            System.out.println("---------------------------------------------------");
            System.out.println("Type in the number for the functionality you want:");
            System.out.println("1 : FTP zone calculation w weight");
            System.out.println("2 : FTP zone calculation w/o weight");
            System.out.println("3 : Parse a .fit activity file");
            System.out.println("4 : Parse multiple .fit activity files");

            System.out.println("0 : Exit program");
        }
        public boolean goInto(Scanner scanner, boolean active){
            int firstInput = scanner.nextInt();
            if(firstInput==1) {
                System.out.println("Let's start with the athlete name");
                String name = scanner.next();
                System.out.println("Please specify the weight of the athlete in question. ");
                double weight = scanner.nextDouble();
                System.out.println("The power from the 20 min test.");
                double power = scanner.nextDouble();
                ftp myFtp = new ftp(name, weight);
                myFtp.ftpCalc(power);
            }
            else if(firstInput==2){
                System.out.println("Let's start with the athlete name");
                String name = scanner.next();
                System.out.println("The power from the 20 min test.");
                double power = scanner.nextDouble();
                ftp myFtp = new ftp(name, null);
                myFtp.ftpCalc(power);
            }
            else if(firstInput==3){
                System.out.println("Please select a FIT activity file to parse.");
                Path file = FitFileUsage.chooseFitFile();
                if (file == null) {
                    System.out.println("No FIT file selected.");
                    return active;
                }

                try {
                    FitActivity activity = new FitParser().parse(file);
                    System.out.println("Parsed " + activity.records().size() + " records.");
                    System.out.println("Start: " + activity.startTime());
                    System.out.println("End: " + activity.endTime());
                    System.out.println("Duration: " + FitFileUsage.formatDuration(activity.duration()));
                    FitFileUsage.printAverage("Average power", activity.averagePower(), " W");
                    FitFileUsage.printAverage("Average speed", activity.averageSpeed(), " km/h");
                    FitFileUsage.printAverage("Average cadence", activity.averageCadence(), " rpm");
                    FitFileUsage.printAverage("Average heart rate", activity.averageHeartRate(), " bpm");
                    if (activity.functionalThresholdPower() != null) {
                        System.out.println("Head unit FTP: " + activity.functionalThresholdPower() + " W");
                        System.out.printf("Intensity factor: %.3f%n", activity.intensityFactor());
                        System.out.printf("training stress score: %.2f%n", activity.TSS());
                    } else {
                        System.out.println("Head unit FTP: unavailable (not stored in the FIT file).");
                        System.out.println("Intensity factor: unavailable (FTP was not stored in the FIT file).");
                        System.out.println("Training stress score: unavailable (power was not stored in the FIT file).");
                    }
                    if (activity.deviceName() != null) {
                        System.out.println("Device: " + activity.deviceName());
                    }
                    boolean m = true;
                    while(m) {
                        System.out.println("What do you want shown? ");
                        System.out.println("0: Nothing other than averages");
                        System.out.println("1: Show the power graph");
                        System.out.println("2: Show the best Power graph");
                        System.out.println("3: Show the best Power/kg graph");
                        System.out.println("4: Show the best power table");
                        System.out.println("5: Show the best Power/kg table");
                        String choice = scanner.next();
                        if (choice.equals("0")) {
                            System.out.println("Returning to the start screen");
                            m =  false;
                        }
                        else if (choice.equals("1")) {
                            Graph.showPowerGraph(activity);
                        }
                        else if (choice.equals("2")) {
                            Graph.showBestPowerGraph(activity);
                        }
                        else if (choice.equals("3")) {
                            System.out.println("Please specify the weight of the athlete in question. ");
                            int w = scanner.nextInt();
                            System.out.println("Showing a graph with best power per weight data");
                            Graph.showPowerWeightGraph(activity, w);
                        }
                        else if (choice.equals("4")) {
                            System.out.println("Please specify the weight of the athlete in question. ");
                            int w = scanner.nextInt();
                            System.out.println("Showing a table with best powerdata");
                            TableBestPowerNumbers.showBestPowerTable(activity, w);
                        }
                        else if (choice.equals("5")) {
                            System.out.println("Please specify the weight of the athlete in question. ");
                            int w = scanner.nextInt();
                            System.out.println("Showing a table with best power per weight data");
                            TableBestPowerNumbers.showBestPowerTable(activity, w);
                        }
                        else {
                            System.out.println("This is not applicable at the moment please try one of the other options. ");
                        }
                    }
                } catch (IOException | RuntimeException exception) {
                    System.out.println("Could not parse FIT file: " + exception.getMessage());
                    if (exception.getCause() != null) {
                        System.out.println("Decoder detail: " + exception.getCause());
                    }
                }
            }
            else if(firstInput==4){
                System.out.println("Enter the directory containing the FIT files:");
                scanner.nextLine();
                String directoryText = scanner.nextLine().trim();
                if (directoryText.isEmpty()) {
                    System.out.println("No directory specified.");
                    return active;
                }
                MultiFileUsage usage = new MultiFileUsage();
                try {
                    usage.loadFiles(Path.of(directoryText));
                    usage.printSummary();
                } catch (IOException | RuntimeException exception) {
                    System.out.println("Could not load FIT files: " + exception.getMessage());
                    return active;
                }
                boolean m = true;
                while(m){
                    System.out.println("What do you want shown? ");
                    System.out.println("0: Nothing other than averages");
                    System.out.println("1: Show the best power graph");
                    System.out.println("2: Show the best Power/kg graph");
                    System.out.println("3: Show the best power table");
                    System.out.println("4: Calculate FTP from the best 20 min");
                    String choice = scanner.next();
                    if (choice.equals("0")) {
                        System.out.println("Returning to the start screen");
                        m =  false;
                    }
                    else if (choice.equals("1")) {
                        System.out.println("Showing a graph with best power data");
                        Graph.showBestPowerGraph(usage.activities());
                    }
                    else if (choice.equals("2")) {
                        System.out.println("Please specify the weight of the athlete in question. ");
                        int w = scanner.nextInt();
                        System.out.println("Showing a graph with best power per weight data");
                        Graph.showPowerWeightGraph(usage.activities(), w);
                    }
                    else if (choice.equals("3")) {
                        System.out.println("Please specify the weight of the athlete in question. ");
                        int w = scanner.nextInt();
                        System.out.println("Showing a table with best power data");
                        TableBestPowerNumbers.showBestPowerTable(usage.activities(), w);
                    }
                    else if (choice.equals("4")) {
                        System.out.println("Calculating FTP from the best 20 min");
                        double ftp = BestPowerNumbers.bestTwentyHist(usage.activities());
                        System.out.println("The FTP is: " + ftp);
                    }
                    else {
                        System.out.println("This is not applicable at the moment please try one of the other options. ");
                    }

                }
            }
            else if(firstInput==0){
                System.out.println("See you later. ");
                active = false;
            }
            else{
                System.out.println("This is not applicable at the moment please try one of the other options. ");

            }
            return active;
        }


}
