package com.aol.cyclops.matcher;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.Wither;

import org.pcollections.ConsPStack;
import org.pcollections.PStack;

@AllArgsConstructor
public class Cases<T,R> implements Function<T,Optional<R>> {
	@Wither
	private final PStack<Case<T,R>> cases;
	@Wither(AccessLevel.PRIVATE)
	private final boolean sequential;

	public Cases() {
		cases = ConsPStack.empty();
		sequential =true;
	}

	private static <T> Collector<T, PStack<T>, PStack<T>> collector() {
		final Supplier<PStack<T>> supplier = ConsPStack::empty;
		final BiConsumer<PStack<T>, T> accumulator = PStack::plus;
		final BinaryOperator<PStack<T>> combiner = (left, right) -> {
			left.plusAll(right);
			return left;
		};

		return Collector.of(supplier, accumulator, combiner);
	}
	
	/**
	 * This is the default behaviour, cases are matched sequentially when a matching function is called
	 * @return New Cases object that will match sequentially 
	 * 
	 */
	public Cases<T,R> sequential(){
		return this.withSequential(true);
	}
	/**
	 * Default behaviour is to match sequentially
	 * @return A new cases object that will match in parallel
	 */
	public Cases<T,R> parallel(){
		return this.withSequential(false);
	}
	
	
	public Cases<T,R> merge(Cases<T, R> patterns){
		return this.withCases(cases.plusAll(size(), patterns.cases));
	}

	public Cases<T,R> filter(Predicate<Case<T,R>> predicate) {
		return withCases(cases.stream().filter(data -> predicate.test(data))
				.collect(collector()));
	}

	public Cases<T,R> filterPredicate(Predicate<Predicate<T>> predicate) {
		return withCases(cases.stream()
				.filter(data -> predicate.test(data.getPredicate()))
				.collect(collector()));
	}

	public Cases<T,R> filterFunction(Predicate<Function<T,R>> predicate) {
		return withCases(cases.stream()
				.filter(data -> predicate.test(data.getAction()))
				.collect(collector()));
	}

	public Cases<T,R> mapPredicate(Function<Predicate<T>, Predicate<T>> predicateMapper) {
		return map(caseData -> {
			return Case.of(predicateMapper.apply(caseData.getPredicate()),
					caseData.getAction());
		});
	}

	public <R1> Cases<T,R> mapFunction(
			Function<Function<T,R>, Function<T,R1>> actionMapper) {
		return map(caseData -> {
			return Case.of(caseData.getPredicate(),
					actionMapper.apply(caseData.getAction()));
		});
	}

	public <T1,R1> Cases<T,R> map(Function<Case<T,R>, Case<T1,R1>> mapper) {

		return this.withCases((PStack)cases.stream().map(mapper).collect(collector()));

	}

	public <T1,R1> Cases<T1,R1> flatMap(Function<PStack<Case<T,R>>, Cases<T1,R1>> mapper) {
		return mapper.apply(cases);
	}

	public Cases append(int index, Case pattern) {
		return this.withCases(cases.plus(index, pattern));
	}

	public int size() {
		return cases.size();
	}

	public <T1, X> Function<T1, X> asUnwrappedFunction() {
		return (T1 t) -> (X) apply((T)t).get();
	}

	public <T1, X> Function<T1, Stream<X>> asStreamFunction() {

		return (T1 t) -> (Stream<X>) Stream.of(t).map(input-> this.apply((T)input))
				.filter(Optional::isPresent).map(Optional::get);
	}

	/*
	 * @param t Object to match against
	 * 
	 * @return Value from matched case if present
	 * 
	 * @see java.util.function.Function#apply(java.lang.Object)
	 */
	public Optional<R> apply(T t) {
		return match(t);
	}

	/**
	 * Each input element can generated multiple matched values
	 * 
	 * @param s
	 *            Stream of data to match against (input to matcher)
	 * @return Stream of values from matched cases
	 */
	public <R> Stream<R> matchManyFromStream(Stream<T> s) {
		return s.flatMap(this::matchMany);
	}
	public <R> CompletableFuture<Stream<R>> matchManyFromStreamAsync(Executor executor, Stream s){
		return CompletableFuture.supplyAsync(()->matchManyFromStream(s), executor);
	}

	/**
	 * 
	 * @param t
	 *            input to match against - can generate multiple values
	 * @return Stream of values from matched cases for the input
	 */
	public <R> Stream<R> matchMany(T t) {
		return (Stream) stream().map(pattern -> pattern.match(t))
				.filter(Optional::isPresent).map(Optional::get);

	}
	public <R> CompletableFuture<Stream<R>> matchManyAsync(Executor executor, T t){
		return CompletableFuture.supplyAsync(()->matchMany(t), executor);
	}
	/**
	 * Each input element can generated a single matched value
	 * 
	 * @param s
	 *            Stream of data to match against (input to matcher)
	 * @return Stream of matched values, one case per input value can match
	 */
	public <R> Stream<R> matchFromStream(Stream s) {

		Stream<Optional<R>> results = s.<Optional<R>> map(this::match);
		return results.filter(Optional::isPresent).map(Optional::get);
	}
	public <R> CompletableFuture<Stream<R>> matchFromStreamAsync(Executor executor, Stream s){
		return CompletableFuture.supplyAsync(()->matchFromStream(s), executor);
	}
	
	public <R> Optional<R> match(Object... t) {
		return match(Arrays.asList(t));
	}
	public <R> CompletableFuture<Optional<R>> matchAsync(Executor executor, Object... t){
		return CompletableFuture.supplyAsync(()->match(t), executor);
	}
	public <R> Optional<R> unapply(Decomposable t) {
		return match(t.unapply());
	}

	/**
	 * @param t
	 *            Object to match against supplied cases
	 * @return Value returned from matched case (if present) otherwise
	 *         Optional.empty()
	 */
	public <R> Optional<R> match(T t) {

		return (Optional) stream().map(pattern -> pattern.match(t))
				.filter(Optional::isPresent).map(Optional::get).findFirst();

	}
	
	
	private Stream<Case<T,R>> stream(){
		if(this.sequential)
			return cases.stream();
		return cases.parallelStream();
	}

}
