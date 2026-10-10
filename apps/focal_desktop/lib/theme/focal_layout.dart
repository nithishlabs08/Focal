/// LocalSend-style layout rhythm for desktop panes.
class FocalLayout {
  static const double maxContentWidth = 600;

  /// Desktop horizontal inset (LocalSend ~15–20px).
  static const double horizontalPadding = 20;

  /// Min width for two-column device grid.
  static const double twoColumnMinWidth = 560;

  /// Between major blocks (title → card, card → list).
  static const double blockGap = 24;

  /// Between stacked cards / list rows.
  static const double cardGap = 12;

  static const double sectionSpacing = cardGap;

  static const double pageVerticalPadding = 20;
  static const double pageBottomPadding = 48;

  static const double titleBottomGap = 8;
  static const double subtitleBottomGap = 20;

  static const double cardPadding = 16;

  static const double rowIconGap = 14;

  static const double bigButtonMinWidth = 200;
}
