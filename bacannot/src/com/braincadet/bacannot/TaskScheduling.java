package com.braincadet.bacannot;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

class Task extends TimerTask {

    int count = 1;

    public void run() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        System.out.println(count + "  " + dateFormat.format(new Date()));
        count++;
    }

}

public class TaskScheduling {

    static Timer timer = new Timer();

    public static void main(String[] args) {

        float periodSec = 0.2f;

        timer.scheduleAtFixedRate(new Task(), 0, Math.round(periodSec*1000));

        Scanner keyboard = new Scanner(System.in);

        boolean exit = false;

        while (!exit) {

            String input = keyboard.nextLine();

            if(input.isEmpty()) {

                timer.cancel();

                exit = true;

            }
        }

        keyboard.close();

    }

}
