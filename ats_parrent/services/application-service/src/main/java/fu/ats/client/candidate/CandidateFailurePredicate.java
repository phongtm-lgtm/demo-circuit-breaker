package fu.ats.client.candidate;

import feign.FeignException;

import java.util.function.Predicate;

public class CandidateFailurePredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable error) {
        return error instanceof FeignException feign && feign.status() >= 500;
    }
}
