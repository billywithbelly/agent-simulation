package utils;

import java.util.Calendar;
import java.util.Date;

public class SimTimer {

    private int hour; // 0-23 hours
    private int minute; // 0-59 minutes
    private int second; // 0-59 seconds
    private double grain; // 0-3600 seconds

    public SimTimer(int h, int m, int s, double g) {
        this.hour = h;
        this.minute = m;
        this.second = s;
        this.grain = g;
    }

    public SimTimer(Date time, double grain) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(time);
        this.hour = calendar.get(Calendar.HOUR_OF_DAY);
        this.minute = calendar.get(Calendar.MINUTE);
        this.second = calendar.get(Calendar.SECOND);
        this.grain = grain;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public int getSecond() {
        return second;
    }

    public void setSecond(int second) {
        this.second = second;
    }

    public double getGrain() {
        return grain;
    }

    public void setGrain(double grain) {
        this.grain = grain;
    }

    public void tick() // how the clock works
    {
        second += grain;

        if (second >= 60.0) // Method for outputting seconds
        {
            minute += (int) (second / 60);
            second %= 60;
        }

        if (minute > 59) // Method for outputting minutes
        {
            hour += minute / 60;
            minute %= 60;
        }

        if (hour >= 24) // Method for outputting hours
        {
            hour = 0;
        }
    }

    public Date getDate() {
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR, hour);
        time.set(Calendar.MINUTE, minute);
        time.set(Calendar.SECOND, second);
        return time.getTime();
    }

    public String toString() //Output time
    {
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }
}

