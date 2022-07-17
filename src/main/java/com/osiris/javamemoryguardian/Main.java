package com.osiris.javamemoryguardian;

import com.osiris.jprocesses2.JProcess;
import com.osiris.jprocesses2.ProcessUtils;
import com.osiris.jprocesses2.util.OS;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PhysicalMemory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class Main {
    public static File jdkDir = null;
    public static File heapDir = null;
    public static File jpsExe = null;
    public static File jcmdExe = null;
    public static String jarName = null;
    public static int maxMB = 4000;
    public static int maxVirtualMB = -1;
    public static String jarPID;
    public static void main(String[] args) throws IOException {
        // TODO save the last arguments used to a file to use them when starting without arguments
        System.out.println("jdk-dir: The jdk directory.");
        System.out.println("heap-dir: The directory to create the <jar-name><jar-pid>.hprof heap-dump file in.");
        System.out.println("jar-name: The currently running jar name to be scanned by Java-Memory-Guardian.");
        System.out.println("max-mb (optional | default 4000MB): The maximum amount of memory to wait until performing a heap-dump and exiting.");
        System.out.println("max-virtual-mb (optional | default -1MB): The maximum amount of virtual memory to wait until performing a heap-dump and exiting.");
        System.out.println("Usage: java -jar jmg.jar jdk-dir <path> heap-dir <path> jar-name <name> max-mb <value>");

        // JDK-DIR
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.contains("jdk-dir")){
                jdkDir = new File(args[i+1]);
                if(jdkDir.isFile()) throw new IllegalArgumentException(jdkDir+" cannot be a file, must be directory!");
                jpsExe = new File(jdkDir+"/bin/jps" + (OS.isWindows ? ".exe" : ""));
                jcmdExe = new File(jdkDir+"/bin/jcmd" + (OS.isWindows ? ".exe" : ""));
                System.out.println("Set jdk-dir to: "+jdkDir);
                break;
            }
        }
        if(jdkDir == null) throw new NullPointerException("jdk-dir cannot be null!");

        // HEAP-DIR
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.contains("heap-dir")){
                heapDir = new File(args[i+1]);
                if(heapDir.isFile()) throw new IllegalArgumentException(heapDir+" cannot be a file, must be directory!");
                System.out.println("Set heap-dir to: "+heapDir);
                break;
            }
        }
        if(heapDir == null) throw new NullPointerException("heap-dir cannot be null!");

        // JAR-NAME
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.contains("jar-name")){
                jarName = args[i+1];
                if(!jarName.endsWith(".jar")) jarName = jarName + ".jar";
                System.out.println("Set jar-name to: "+jarName);
                break;
            }
        }
        if(jarName == null) throw new NullPointerException("jar-name cannot be null!");
        Process pCheckJarName = new ProcessBuilder().command(jpsExe.getAbsolutePath()).start();
        List<String> pCheckJarNameOutput = Streams.toList(pCheckJarName.getInputStream());
        for (String line : pCheckJarNameOutput) {
            if(line.contains(jarName)){
                jarPID = line.split(" ")[0].trim();
                break;
            }
        }
        if(jarPID == null)
            throw new NullPointerException("Failed to find a running process named '"+jarName+"' via jps. Full output below:\n"
                    +pCheckJarNameOutput);

        // MAX-MB
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.contains("max-mb")){
                maxMB =  Integer.parseInt(args[i+1]);
                System.out.println("Set max-mb to: "+maxMB);
                break;
            }
        }

        //MAX-VMB
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.contains("max-virtual-mb")){
                maxVirtualMB =  Integer.parseInt(args[i+1]);
                System.out.println("Set max-virtual-mb to: "+maxVirtualMB);
                break;
            }
        }

        new Thread(() -> {
            try{
                ProcessUtils processUtils = new ProcessUtils();
                SystemInfo si = new SystemInfo();
                HardwareAbstractionLayer hal = si.getHardware();
                GlobalMemory memory = hal.getMemory();
                int totalMem = (int) (memory.getTotal()/1000000);
                while (true){
                    List<JProcess> list = processUtils.getProcesses();
                    JProcess jarProcess = null;
                    boolean exit = false;
                    for (JProcess process : list) {
                        if(process.pid.equals(jarPID)){
                            jarProcess = process;
                            int mb = Integer.parseInt(process.usedMemoryInKB) / 1000;
                            int vmb = Integer.parseInt(process.usedVirtualMemoryInKB) / 1000;
                            int freeMem = (int) (memory.getAvailable()/1000000);
                            int usedMem = totalMem - freeMem;
                            int totalVirtualMem = (int) (memory.getVirtualMemory().getVirtualMax() / 1000000);
                            int usedVirtualMem = (int) (memory.getVirtualMemory().getVirtualInUse() /1000000);
                            int freeVirtualMem = totalVirtualMem - usedVirtualMem;

                            System.out.println(new Date().toString()+" PID="+jarPID+" MB="+mb+" VMB="+vmb+"\n"+
                                    "USAGE="+usedMem+ "/"+totalMem+"MB FREE="+freeMem+"MB USAGE-VIRTUAL="+usedVirtualMem+ "/"+totalVirtualMem+"MB FREE="+freeVirtualMem+"MB ");
                            if(mb > maxMB || (maxVirtualMB >= 0 && vmb > maxVirtualMB)){
                                System.out.println(new Date().toString()+" PID="+jarPID+" MB="+mb+" OR VMB="+vmb+" BIGGER THAN MAX! CREATING HEAP-DUMP...");
                                File heapDump = new File(heapDir + "/" + jarName + jarPID + ".hprof");
                                Process pCreateHeapDump = new ProcessBuilder().command(
                                        jcmdExe.getAbsolutePath(),
                                        jarPID, "GC.heap_dump", "\"" +heapDump+ "\"").start();
                                pCreateHeapDump.waitFor();
                                if(pCreateHeapDump.exitValue() != 0)
                                    throw new RuntimeException("Failed to create heap-dump "+heapDump+" exit code is not 0 but "+pCreateHeapDump.exitValue()+"!\n" +
                                            "output: \n"+ Streams.toString(pCreateHeapDump.getInputStream())+" \n error: \n"
                                            +Streams.toString(pCreateHeapDump.getErrorStream()));
                                System.out.println(new Date().toString()+" DONE -> "+heapDump);
                                exit = true;
                            }
                            break;
                        }
                    }
                    if(exit) break;
                    if(jarProcess==null)
                        throw new RuntimeException("Failed to find an active process with PID: "+jarPID);
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        System.out.println("Started thread! Now watching PID "+jarPID+" "+jarName+" and waiting until its memory is above "+maxMB+" MB to create heap-dump.");
    }

}
