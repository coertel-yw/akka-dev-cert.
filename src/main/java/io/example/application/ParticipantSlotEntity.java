package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantType;

import java.util.Objects;

@ComponentId("participant-slot")
public class ParticipantSlotEntity
        extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

    public Effect<Done> unmarkAvailable(ParticipantSlotEntity.Commands.UnmarkAvailable unmark) {
        if (currentState() != null && !Objects.equals(currentState().status, "available")) {
            return effects().error("Cannot mark a time-slot as unavailable for which participant has not been marked available");
        }

        return effects()
                .persist(
                        new Event.UnmarkedAvailable(unmark.slotId(), unmark.participantId(), unmark.participantType())
                )
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> markAvailable(ParticipantSlotEntity.Commands.MarkAvailable mark) {
        if (currentState() != null && Objects.equals(currentState().status, "booked")) {
            return effects().error("Cannot mark a time-slot as available for which participant has been marked marked as booked");
        }

        return effects()
                .persist(
                        new Event.MarkedAvailable(mark.slotId(), mark.participantId(), mark.participantType())
                )
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> book(ParticipantSlotEntity.Commands.Book book) {
        if (currentState() != null && !Objects.equals(currentState().status, "available")) {
            return effects().error("Cannot book a time-slot for which participant has not been marked available");
        }

        return effects()
                .persist(
                        new Event.Booked(book.slotId(), book.participantId(), book.participantType(), book.bookingId())
                )
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> cancel(ParticipantSlotEntity.Commands.Cancel cancel) {
        if (currentState() != null && !Objects.equals(currentState().status, "booked")) {
            return effects().error("Cannot cancel time-slot for which participant has not been marked as booked");
        }

        return effects()
                .persist(
                        new Event.Canceled(cancel.slotId(), cancel.participantId(), cancel.participantType(), cancel.bookingId())
                )
                .thenReply(__ -> Done.done());
    }

    record State(
            String slotId, String participantId, ParticipantType participantType, String status) {
    }

    public sealed interface Commands {
        record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record Book(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }

        record Cancel(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }
    }

    public sealed interface Event {
        String slotId();
        String participantId();
        ParticipantType participantType();

        @TypeName("marked-available")
        record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("unmarked-available")
        record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("participant-booked")
        record Booked(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }

        @TypeName("participant-canceled")
        record Canceled(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }
    }

    @Override
    public ParticipantSlotEntity.State applyEvent(ParticipantSlotEntity.Event event) {
        var newStatus = switch (event) {
            case Event.MarkedAvailable __ -> "available";
            case Event.UnmarkedAvailable __ -> "unavailable";
            case Event.Booked __ -> "booked";
            case Event.Canceled __ -> "available";
        };

        return new ParticipantSlotEntity.State(
                event.slotId(),
                event.participantId(),
                event.participantType(),
                newStatus
        );
    }
}
