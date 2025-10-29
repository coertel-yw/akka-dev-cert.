package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@ComponentId("booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        var state = currentState();

        if (state != null && state.bookings().stream().anyMatch(b -> b.participant() == cmd.participant())) {
            // Already marked as booked for this timeslot
            logger.warn("{} {} already has a booking for slot {}, cannot mark as available", cmd.participant().participantType().name(), cmd.participant().id(), entityId);
            return effects().error("Participant already has a booking for this timeslot");
        }
        if (state != null && state.isWaiting(cmd.participant().id(), cmd.participant().participantType())) {
            // Already marked as available
            logger.info("{} {} already marked available for slot {}", cmd.participant().participantType().name(), cmd.participant().id(), entityId);
            return effects().reply(Done.done());
        }

        logger.info("Marking {} {} available for slot {}", cmd.participant().participantType().name(), cmd.participant().id(), entityId);

        return effects()
                .persist(new BookingEvent.ParticipantMarkedAvailable(entityId, cmd.participant().id(), cmd.participant().participantType()))
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        var state = currentState();

        if (state != null && state.bookings().stream().anyMatch(b -> b.participant() == cmd.participant())) {
            // Already marked as booked for this timeslot
            logger.warn("{} {} already has a booking for slot {}, cannot mark as unavailable", cmd.participant().participantType().name(), cmd.participant().id(), entityId);
            return effects().error("Participant has a booking for this timeslot, please cancel booking first");
        }
        if (state != null && !state.isWaiting(cmd.participant().id(), cmd.participant().participantType())) {
            // Not marked as available
            logger.info("{} {} not marked available for slot {}", cmd.participant().participantType().name(), cmd.participant().id(), entityId);
            return effects().reply(Done.done());
        }

        logger.info("Marking {} {} unavailable for slot {}", cmd.participant().participantType().name(), cmd.participant().id(), entityId);

        return effects()
                .persist(new BookingEvent.ParticipantUnmarkedAvailable(entityId, cmd.participant().id(), cmd.participant().participantType()))
                .thenReply(__ -> Done.done());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        var state = currentState();

        if (state != null && !state.isBookable(cmd.studentId(), cmd.aircraftId(), cmd.instructorId())) {
            logger.info("Cannot book {} in slot {} for student {}, aircraft {} and instructor {}, not all resources are available", cmd.bookingId, entityId, cmd.studentId(), cmd.aircraftId(), cmd.instructorId());
            return effects().error("Booking cannot be made, as not all required participants are available");
        }

        logger.info("Booking {} in slot {} for student {}, aircraft {} and instructor {}", cmd.bookingId, entityId, cmd.studentId(), cmd.aircraftId(), cmd.instructorId());

        return effects()
                .persistAll(
                        List.of(
                                new BookingEvent.ParticipantBooked(entityId, cmd.studentId(), Participant.ParticipantType.STUDENT, cmd.bookingId),
                                new BookingEvent.ParticipantBooked(entityId, cmd.instructorId(), Participant.ParticipantType.INSTRUCTOR, cmd.bookingId),
                                new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId(), Participant.ParticipantType.AIRCRAFT, cmd.bookingId)
                        )
                )
                .thenReply(__ -> Done.done());
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        var bookings = currentState()
                .bookings()
                .stream()
                .filter(b -> b.bookingId().equals(bookingId))
                .toList();

        if (bookings.isEmpty()) {
            logger.warn("Cannot cancel booking {} in slot {}, booking does not exist", bookingId, entityId);
            return effects().error("Booking does not exist");
        }

        logger.info("Cancelling booking {} in slot {}", bookingId, entityId);

        var cancellations = bookings
                .stream()
                .map(b -> new BookingEvent.ParticipantCanceled(entityId, b.participant().id(), b.participant().participantType(), bookingId))
                .toList();

        return effects()
                .persistAll(cancellations)
                .thenReply(__ -> Done.done());
    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        var timeslot = currentState();
        var bookings = timeslot.bookings();
        var available = timeslot.available();

        switch (event) {
            case BookingEvent.ParticipantBooked e -> {
                bookings.add(new Timeslot.Booking(new Participant(e.participantId(), e.participantType()), e.bookingId()));
                available.removeIf(a -> Objects.equals(a.id(), e.participantId()));
            }
            case BookingEvent.ParticipantCanceled e -> {
                // Not checking for the booking-ID as there can only be one booking per participant per timeslot.
                bookings.removeIf(b -> Objects.equals(b.participant().id(), e.participantId()));

                // I would have considered a cancellation makes the resources/participants available again
                // available.add(new Participant(e.participantId(), e.participantType()));
            }
            case BookingEvent.ParticipantMarkedAvailable e ->
                available.add(new Participant(e.participantId(), e.participantType()));
            case BookingEvent.ParticipantUnmarkedAvailable e ->
                available.removeIf(a -> Objects.equals(a.id(), e.participantId()));
        }

        // As we're mutating sets ;-(, creating a new timeslot instance is strictly speaking not necessary.
        return new Timeslot(bookings, available);
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
