package com.itson.moderator.config;

/**
 * A selectable reason from {@code reasons} in config.yml.
 *
 * <p>Reasons exist so staff can run {@code /mod ban Steve cheating} instead of
 * typing a sentence, and so every sanction is categorised the same way.
 *
 * @param id       lower case key used on the command line
 * @param label    the text stored in the history and shown to the punished player
 * @param applies  punishment types this reason may be used for, empty means all
 */
public record Reason(String id, String label, java.util.Set<com.itson.moderator.model.PunishmentType> applies) {

  public Reason {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }

    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }

    if (applies == null) {
      throw new IllegalArgumentException("applies must not be null");
    }
  }

  /** True when this reason is offered for the given punishment type. */
  public boolean supports(com.itson.moderator.model.PunishmentType type) {
    return applies.isEmpty() || applies.contains(type);
  }
}
