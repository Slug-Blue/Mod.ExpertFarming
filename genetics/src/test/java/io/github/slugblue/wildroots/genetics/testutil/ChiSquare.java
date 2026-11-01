package io.github.slugblue.wildroots.genetics.testutil;

/** χ² goodness of fit for statistical acceptance tests (fixed seeds, p &gt; 0.01). */
public final class ChiSquare {

    private ChiSquare() {
    }

    /** Statistic Σ (o − e)² / e for counts and expected probabilities. */
    public static double statistic(long[] observed, double[] probabilities) {
        long n = 0;
        for (long o : observed) {
            n += o;
        }
        double psum = 0;
        for (double p : probabilities) {
            psum += p;
        }
        double chi = 0;
        for (int i = 0; i < observed.length; i++) {
            double e = n * probabilities[i] / psum;
            chi += (observed[i] - e) * (observed[i] - e) / e;
        }
        return chi;
    }

    /** Upper-tail p-value of χ² with {@code df} degrees of freedom. */
    public static double pValue(double chi, int df) {
        return 1 - regularizedGammaP(df / 2.0, chi / 2.0);
    }

    public static double pValue(long[] observed, double[] probabilities) {
        return pValue(statistic(observed, probabilities), observed.length - 1);
    }

    /** Regularized lower incomplete gamma P(a, x) (Numerical Recipes: series or continued fraction). */
    static double regularizedGammaP(double a, double x) {
        if (x <= 0) {
            return 0;
        }
        double gln = logGamma(a);
        if (x < a + 1) {
            double ap = a;
            double sum = 1.0 / a;
            double del = sum;
            for (int n = 0; n < 10_000; n++) {
                ap += 1;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * 1e-15) {
                    break;
                }
            }
            return sum * Math.exp(-x + a * Math.log(x) - gln);
        }
        double b = x + 1 - a;
        double c = 1.0 / 1e-300;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i < 10_000; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < 1e-300) {
                d = 1e-300;
            }
            c = b + an / c;
            if (Math.abs(c) < 1e-300) {
                c = 1e-300;
            }
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-15) {
                break;
            }
        }
        return 1 - Math.exp(-x + a * Math.log(x) - gln) * h;
    }

    /** Lanczos approximation of ln Γ(x). */
    static double logGamma(double x) {
        double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155,
            0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double cj : c) {
            ser += cj / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
