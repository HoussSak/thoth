package fr.maif.eventsourcing;

import io.vavr.Tuple;
import io.vavr.Tuple0;
import io.vavr.collection.List;
import io.vavr.control.Option;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

public interface ReactorAggregateStore<S extends State<S>, Id, TxCtx> {

    Mono<Option<S>> getAggregate(Id entityId);

    Mono<Option<S>> getAggregate(TxCtx ctx, Id entityId);

    default Mono<Tuple0> storeSnapshot(TxCtx transactionContext, Id id, Option<S> state) {
        return Mono.just(Tuple.empty());
    }

    default Mono<Option<S>> getSnapshot(TxCtx transactionContext, Id id) {
        return Mono.just(Option.none());
    }

    default <E extends Event> Mono<Option<S>> buildAggregateAndStoreSnapshot(TxCtx ctx, EventHandler<S, E> eventHandler, Option<S> state, Id id, List<E> events, Option<Long> lastSequenceNum) {

        Option<S> newState = eventHandler.deriveState(state, events.filter(event -> event.entityId().equals(id)));

        Option<S> newStatewithSequence = lastSequenceNum
                .map(num -> newState.map(s -> (S) s.withSequenceNum(num)))
                .getOrElse(newState);

        return storeSnapshot(ctx, id, newStatewithSequence).map(__ -> newState);
    }

    default AggregateStore<S, Id, TxCtx> toAggregateStore() {
        var _this = this;
        return new AggregateStore<S, Id, TxCtx>() {
            @Override
            public CompletionStage<Option<S>> getAggregate(Id entityId) {
                return _this.getAggregate(entityId).toFuture();
            }

            @Override
            public CompletionStage<Option<S>> getAggregate(TxCtx txCtx, Id entityId) {
                return _this.getAggregate(txCtx, entityId).toFuture();
            }
        };
    }


    static <S extends State<S>, Id, TxCtx> ReactorAggregateStore<S, Id, TxCtx> fromAggregateStore(AggregateStore<S, Id, TxCtx> aggregateStore) {
        return new ReactorAggregateStore<S, Id, TxCtx>() {

            @Override
            public Mono<Option<S>> getAggregate(Id entityId) {
                return Mono.fromCompletionStage(() -> aggregateStore.getAggregate(entityId));
            }

            @Override
            public Mono<Option<S>> getAggregate(TxCtx txCtx, Id entityId) {
                return Mono.fromCompletionStage(() -> aggregateStore.getAggregate(txCtx, entityId));
            }
        };
    }

}
