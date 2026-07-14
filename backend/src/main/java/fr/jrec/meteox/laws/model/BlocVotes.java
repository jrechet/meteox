package fr.jrec.meteox.laws.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Répartition des votes d'un bloc politique sur un scrutin public (données officielles AN). */
public record BlocVotes(
    @JsonProperty("for") int votesFor,
    @JsonProperty("against") int votesAgainst,
    @JsonProperty("abstained") int votesAbstained) {}
