package com.sni.bilanga.security.authentication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Le compte authentifié, tel que {@code GET /auth/me} le rend.
 *
 * <h2>⚠️ Deux identifiants, et il faut savoir lequel employer</h2>
 *
 * <p>{@code Users} en porte deux, et ce n'est pas un doublon : {@link #id} est la clé
 * technique (Snowflake), {@link #userId} une référence lisible qu'on peut dicter au
 * téléphone ou inscrire sur un document.
 *
 * <table>
 *   <caption>Lequel passer, et où</caption>
 *   <tr><th>Champ</th><th>Exemple</th><th>Attendu par</th></tr>
 *   <tr><td>{@link #id}</td><td>{@code "1934567890123456789"}</td>
 *       <td>{@code plots.userId}, {@code alerts.assignedToUserId}, tout champ
 *           {@code …Id} d'un corps de requête</td></tr>
 *   <tr><td>{@link #userId}</td><td>{@code "USR-300726-82U3GVS3"}</td>
 *       <td>les <strong>chemins</strong> d'administration : {@code /admin/users/{userCode}}</td></tr>
 * </table>
 *
 * <p><strong>Le piège que {@link #id} corrige.</strong> Cette réponse ne portait que
 * {@code userId}, dont le nom laissait croire qu'il allait dans un champ {@code userId} de
 * requête. Il n'y va pas — et comme <em>tous</em> les identifiants sortent en chaînes, rien
 * ne distinguait visuellement les deux. Le résultat était un 400 « Request body is missing
 * or malformed » qui ne nommait même pas le champ fautif.
 *
 * <p>Dans la plupart des cas, la question ne se pose plus : {@code POST /plots} attribue la
 * parcelle à l'appelant quand {@code userId} est omis.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CurrentUserResponse {

    /**
     * Clé technique (Snowflake), <strong>sérialisée en chaîne</strong>.
     *
     * <p>C'est celle qu'attendent les champs {@code …Id} des corps de requête.
     */
    private Long id;

    /**
     * Référence lisible — {@code USR-300726-82U3GVS3}.
     *
     * <p>Employée dans les <strong>chemins</strong> d'administration. Nom conservé tel
     * quel : le renommer casserait les clients existants.
     */
    private String userId;

    private String email;
    private boolean accountEnabled;
    private List<String> authorities;
}
