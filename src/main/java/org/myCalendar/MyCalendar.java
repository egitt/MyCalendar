package org.myCalendar;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.*;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.transform.recurrence.Frequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class MyCalendar {

    private static final Logger log = LoggerFactory.getLogger(MyCalendar.class);

    private enum PropertyName { UID, DTSTAMP , DTSTART, SUMMARY }
    private final Map<String, VEvent> calendarDataByUid = new HashMap<>();

    public static void main(String[] args) {
        MyCalendar myCalendar = new MyCalendar();
        myCalendar.start();
    }

    private void start() {
        Calendar calendarIn = slurpCalendar();

        Calendar calendarOut = parseCalendar(calendarIn);

        outputCalendar(calendarOut);
    }

    private Calendar slurpCalendar() {
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar;
        try {
            calendar = builder.build(new FileInputStream("/home/boromir/Downloads/Kalender/Jahrestage-2024-12-29.ics"));
        } catch (IOException | ParserException e) {
            throw new RuntimeException(e);
        }
        return calendar;
    }

    private Calendar parseCalendar(Calendar calendarIn) {
        Calendar calendarOut = new Calendar();

        Calendar.MERGE_PROPERTIES.apply(calendarOut, calendarIn.getProperties());

        List<CalendarComponent> componentTimeZoneList = calendarIn.getComponents().stream().filter(object -> object.getClass().equals(VTimeZone.class)).toList();
        Calendar.MERGE_COMPONENTS.apply(calendarOut, new ArrayList<>(componentTimeZoneList));

        if (calendarIn.getComponents() != null) {
            List<CalendarComponent> componentEventList = calendarIn.getComponents().stream().filter(object -> object.getClass().equals(VEvent.class)).toList();
            for (CalendarComponent calendarComponent : componentEventList) {
                if (calendarComponent.getProperties() != null) {
                    List<Property> propertyList = new ArrayList<>();
                    String uid = null;
                    VEvent vEvent;
                    for (Property property : calendarComponent.getProperties()) {
                        if (property.getName().equals(PropertyName.UID.toString())) {
                            uid = property.getValue();
                            if (uid.endsWith("google.com")) {
                                if (uid.contains("CUSTOM")) {
                                    // event is wedding day or day of death
                                    uid = uid.substring(12);
                                } else {
                                    uid = uid.substring(14);
                                }
                            }
                            vEvent = new VEvent(new PropertyList(propertyList));
                            if (!calendarDataByUid.containsKey(uid)) {
                                propertyList.add(new Uid(uid));
                                propertyList.add(new RRule<LocalDate>(Frequency.YEARLY));
                                calendarDataByUid.put(uid, vEvent);
                            }
                        } else if (property.getName().equals(PropertyName.DTSTAMP.toString())) {
                            String dtStamp = property.getValue();
                            if (calendarDataByUid.containsKey(uid)) {
                                propertyList.add(new DtStamp(dtStamp));
                            }
                        } else if (property.getName().equals(PropertyName.DTSTART.toString())) {
                            String dtStart = property.getValue();
                            if (calendarDataByUid.containsKey(uid)) {
                                VEvent currentEvent = calendarDataByUid.get(uid);
                                if (currentEvent.getDateTimeStart().isEmpty()) {
                                    propertyList.add(new DtStart<>(dtStart));
                                } else if (dtStart.compareTo(currentEvent.getDateTimeStart().get().getValue()) < 0) {
                                    log.info("replacing DtStart with smaller date");
                                    currentEvent.replace(new DtStart<>(dtStart));
                                }
                            }
                        } else if (property.getName().equals(PropertyName.SUMMARY.toString())) {
                            String summary = property.getValue();
                            if (calendarDataByUid.containsKey(uid)) {
                                if (summary.contains(" hat Geburtstag")) {
                                    summary = "Geburtstag: " + property.getValue().replace(" hat Geburtstag", "");
                                }
                                propertyList.add(new Summary(summary));
                            }
                        }
                    }
                }
            }
        }

        List<VEvent> componentEventList = calendarDataByUid.values().stream().filter(object -> object.getClass().equals(VEvent.class)).toList();
        Calendar.MERGE_COMPONENTS.apply(calendarOut, new ArrayList<>(componentEventList));

        return calendarOut;
    }

    private void outputCalendar(Calendar calendarOut) {
        CalendarOutputter outputter = new CalendarOutputter();
        try {
            outputter.output(calendarOut, new FileOutputStream("/home/boromir/Downloads/Kalender/mycalendar.ics"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
