package com.sni.bilanga.templateResponse;

import tools.jackson.databind.annotation.JsonSerialize;
import com.sni.bilanga.utils.json.CounterSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PageInfo {

    private int page;
    private int size;

    /**
     * Un compteur, pas un identifiant : il sort en nombre malgré la règle
     * globale qui transforme les {@code long} en chaînes pour protéger les
     * identifiants Snowflake. Le client n'a plus à convertir avant de calculer
     * un nombre de pages.
     */
    @JsonSerialize(using = CounterSerializer.class)
    private long totalElements;

    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;
    private boolean hasPrevious;
    private SortInfo sort;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SortInfo {
        private boolean sorted;
        private boolean unsorted;
        private String direction;
        private List<String> properties;

        public SortInfo(Sort sort) {
            if (sort == null) {
                this.sorted = false;
                this.unsorted = true;
                this.direction = null;
                this.properties = List.of();
                return;
            }

            this.sorted = sort.isSorted();
            this.unsorted = sort.isUnsorted();
            this.properties = sort.stream().map(Sort.Order::getProperty).toList();
            this.direction = sort.isSorted() && !this.properties.isEmpty()
                    ? sort.iterator().next().getDirection().name()
                    : null;
        }
    }

}
