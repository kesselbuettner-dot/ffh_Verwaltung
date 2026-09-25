package de.bierverein.api;

import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class HolidayService {
    private final AppSettingsRepository settings;
    public HolidayService(AppSettingsRepository settings){this.settings=settings;}
    public Optional<String> name(LocalDate date){
        String state=settings.findAll().stream().findFirst().map(AppSettings::getFederalState).filter(s->!s.isBlank()).orElse("SN");
        Map<LocalDate,String> h=new HashMap<>();int y=date.getYear();LocalDate easter=easter(y);
        h.put(LocalDate.of(y,1,1),"Neujahr");h.put(easter.minusDays(2),"Karfreitag");h.put(easter.plusDays(1),"Ostermontag");
        h.put(LocalDate.of(y,5,1),"Tag der Arbeit");h.put(easter.plusDays(39),"Christi Himmelfahrt");h.put(easter.plusDays(50),"Pfingstmontag");
        h.put(LocalDate.of(y,10,3),"Tag der Deutschen Einheit");h.put(LocalDate.of(y,12,25),"1. Weihnachtstag");h.put(LocalDate.of(y,12,26),"2. Weihnachtstag");
        if(Set.of("BW","BY","ST").contains(state))h.put(LocalDate.of(y,1,6),"Heilige Drei Könige");
        if(Set.of("BW","BY","HE","NW","RP","SL").contains(state))h.put(easter.plusDays(60),"Fronleichnam");
        if(Set.of("BB","MV","SN","ST","TH","HB","HH","NI","SH").contains(state))h.put(LocalDate.of(y,10,31),"Reformationstag");
        if(Set.of("BW","BY","NW","RP","SL").contains(state))h.put(LocalDate.of(y,11,1),"Allerheiligen");
        if("SN".equals(state)){LocalDate nov23=LocalDate.of(y,11,23);h.put(nov23.with(TemporalAdjusters.previous(DayOfWeek.WEDNESDAY)),"Buß- und Bettag");}
        return Optional.ofNullable(h.get(date));
    }
    private LocalDate easter(int year){int a=year%19,b=year/100,c=year%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3,h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451;return LocalDate.of(year,(h+l-7*m+114)/31,(h+l-7*m+114)%31+1);}
}
