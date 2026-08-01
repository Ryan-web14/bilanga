package com.sni.bilanga.enums;

/**
 * Pourquoi une campagne s'est achevée.
 *
 * <p><strong>Le manque comblé.</strong> {@code CropServiceImpl.delete()} se
 * contentait de passer {@code status → TERMINEE}. Une campagne récoltée à terme et
 * une campagne détruite par le mildiou se ressemblaient donc trait pour trait en
 * base — alors que ce sont les deux situations les plus opposées qu'une exploitation
 * puisse connaître.
 *
 * <p><strong>Pourquoi cela compte au-delà de la traçabilité.</strong> Le motif est ce
 * qui rend l'historique de succession <em>interprétable</em>. Un rendement nul après
 * {@code RECOLTE_NORMALE} signale un problème agronomique à chercher ; le même
 * rendement nul après {@code PERTE_CLIMATIQUE} ne signale rien d'autre que la météo.
 * Sans le motif, comparer deux campagnes revient à comparer deux chiffres dont on
 * ignore ce qu'ils mesurent.
 *
 * <p>Miroir exact de la contrainte {@code chk_crops_closure_reason} de la V28 : le
 * vocabulaire est tenu des deux côtés — l'énumération protège l'API, la contrainte
 * protège les données contre une écriture directe.
 */
public enum CropClosureReason {

    /** Terme atteint, récolte faite. Le cas nominal. */
    RECOLTE_NORMALE("Récolte normale", true),

    /**
     * Récoltée avant le terme prévu — opportunité de prix, météo annoncée, maladie
     * naissante. Le rendement est légitimement inférieur au potentiel : le comparer
     * à une récolte à terme sans le savoir serait trompeur.
     */
    RECOLTE_ANTICIPEE("Récolte anticipée", true),

    PERTE_MALADIE("Perte par maladie", false),

    PERTE_CLIMATIQUE("Perte climatique", false),

    PERTE_RAVAGEURS("Perte par ravageurs", false),

    /** Non récoltée, laissée en place. */
    ABANDON("Abandon", false),

    /** Sol retravaillé, culture détruite volontairement. */
    RETOURNEE("Retournée", false),

    /**
     * La campagne n'a jamais existé — saisie erronée.
     *
     * <p>Distinct d'{@code ABANDON} : une campagne abandonnée a bien occupé le sol et
     * consommé des intrants, et elle doit rester dans l'historique de succession.
     * Une erreur de saisie ne doit ni compter dans les statistiques, ni figurer comme
     * précédent cultural.
     */
    ERREUR_DE_SAISIE("Erreur de saisie", false);

    private final String label;

    /**
     * Vrai si la campagne a effectivement produit.
     *
     * <p>Sert à distinguer « rendement nul parce que rien n'a poussé » de « rendement
     * nul parce que la campagne n'a jamais été menée à terme ». Sans cette
     * distinction, une moyenne de rendements mêlerait des campagnes et des accidents.
     */
    private final boolean harvested;

    CropClosureReason(String label, boolean harvested) {
        this.label = label;
        this.harvested = harvested;
    }

    public String getLabel() {
        return label;
    }

    public boolean isHarvested() {
        return harvested;
    }

    /** Vrai pour toute clôture décrivant une perte — utile aux bilans. */
    public boolean isLoss() {
        return this == PERTE_MALADIE || this == PERTE_CLIMATIQUE || this == PERTE_RAVAGEURS;
    }

    /**
     * Vrai si la campagne doit être <strong>écartée</strong> des statistiques et de
     * l'historique de succession. Seule l'erreur de saisie l'est : tout le reste a
     * réellement occupé le sol.
     */
    public boolean isExcludedFromHistory() {
        return this == ERREUR_DE_SAISIE;
    }

    public static CropClosureReason from(String value) {
        return DomainEnums.parse(CropClosureReason.class, value);
    }
}
