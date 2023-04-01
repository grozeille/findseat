package org.grozeille.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WeekDispatchResult {
    private Map<Integer, DayDispatchResult> dispatchPerDayOfWeek = new HashMap<>();
}
