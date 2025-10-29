package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.application.ParticipantSlotEntity.Event.Booked;
import io.example.application.ParticipantSlotEntity.Event.Canceled;
import io.example.application.ParticipantSlotEntity.Event.MarkedAvailable;
import io.example.application.ParticipantSlotEntity.Event.UnmarkedAvailable;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            logger.debug("Processing {}", event);

            return switch (event) {
                case ParticipantSlotEntity.Event.MarkedAvailable e ->
                        effects().updateRow(new SlotRow(e.slotId(), e.participantId(), e.participantType().name(), "", "available"));
                case ParticipantSlotEntity.Event.UnmarkedAvailable e ->
                        effects().updateRow(new SlotRow(e.slotId(), e.participantId(), e.participantType().name(), "", "unavailable"));
                case ParticipantSlotEntity.Event.Booked e ->
                        effects().updateRow(new SlotRow(e.slotId(), e.participantId(), e.participantType().name(), e.bookingId(), "booked"));
                case ParticipantSlotEntity.Event.Canceled e ->
                        effects().updateRow(new SlotRow(e.slotId(), e.participantId(), e.participantType().name(), "", "available"));

            };
        }

        // Deletion?
    }

    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status) {
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }

    public record SlotList(List<SlotRow> slots) {
    }

    @SuppressWarnings("unused")
    @Query("SELECT * AS slots FROM view_participants_slots WHERE participantId = :participantId")
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        return queryResult();
    }

    @SuppressWarnings("unused")
    @Query("SELECT * AS slots FROM view_participants_slots WHERE participantId = :participantId AND status = :status")
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }
}
