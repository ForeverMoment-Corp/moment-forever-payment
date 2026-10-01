package com.forvmom.MomentForeverPayment.events;

public interface OutGoingEvent {
    public String getBookingId();
    public String getEventType();
    public String getEventId();
    public String getProducer();


}
