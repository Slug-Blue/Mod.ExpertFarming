package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;

/**
 * Qualitative (Mendelian) trait, one locus (GDD §3.6, §3.9 {@code qualitative}).
 * Alleles: 0 = wild, 1 = domestic; a multi-allelic S-locus numbers its alleles
 * {@code 0..alleles-1}.
 *
 * @param id           trait id
 * @param dominant     dominant allele (biallelic traits)
 * @param wildFreq     frequency of the domestic allele in wild patches
 * @param toxic        bitter phenotype poisons instead of nausea (GDD §3.6)
 * @param alleles      allele count: 2, or 2–16 for the S-locus
 * @param selfIncompat this locus is the S-locus (GDD §4.2)
 * @param patchAlleles S-alleles present in one wild patch (GDD §3.6: 6 of 10)
 */
public record QualTrait(TraitId id, Dominance dominant, double wildFreq, boolean toxic, int alleles,
                        boolean selfIncompat, int patchAlleles) {

    /** Plain biallelic trait. */
    public static QualTrait biallelic(String id, Dominance dominant, double wildFreq) {
        return new QualTrait(TraitId.of(id), dominant, wildFreq, false, 2, false, 2);
    }

    /** Whether a genotype shows the domestic phenotype (biallelic traits). */
    public boolean domesticPhenotype(int a, int b) {
        return dominant == Dominance.DOMESTIC ? (a == 1 || b == 1) : (a == 1 && b == 1);
    }
}
