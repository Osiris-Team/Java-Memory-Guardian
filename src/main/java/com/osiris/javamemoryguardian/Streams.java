package com.osiris.javamemoryguardian;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Streams {

    public static String toString(InputStream in) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(in))){
            String line;
            while((line = reader.readLine()) != null) stringBuilder.append(line).append("\n");
        }
        return stringBuilder.toString();
    }

    public static List<String> toList(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(in))){
            String line;
            while((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    public static Thread readAllAsync(InputStream in, Consumer<String> onLineRead, Consumer<Exception> onException){
        Thread t = new Thread(() -> {
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(in))){
                String line;
                while((line = reader.readLine()) != null) onLineRead.accept(line);
            } catch (Exception e) {
                onException.accept(e);
            }
        });
        t.start();
        return t;
    }
}
