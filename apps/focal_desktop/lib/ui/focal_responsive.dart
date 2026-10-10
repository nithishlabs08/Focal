import 'package:flutter/material.dart';

/// LocalSend `SizingInformation` breakpoints.
class FocalSizing {
  const FocalSizing(double width)
      : isMobile = width < 700,
        isTabletOrDesktop = width >= 700,
        isDesktop = width >= 800;

  final bool isMobile;
  final bool isTabletOrDesktop;
  final bool isDesktop;

  static FocalSizing of(BuildContext context) =>
      FocalSizing(MediaQuery.sizeOf(context).width);
}

/// Centered column/list with LocalSend default max width 600.
class FocalResponsiveList extends StatelessWidget {
  const FocalResponsiveList({
    super.key,
    required this.children,
    this.padding = EdgeInsets.zero,
    this.desktopPadding = const EdgeInsets.symmetric(horizontal: 10, vertical: 30),
    this.maxWidth = 600,
  });

  final List<Widget> children;
  final EdgeInsets padding;
  final EdgeInsets desktopPadding;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final sizing = FocalSizing.of(context);
    final resolved = sizing.isDesktop ? desktopPadding : padding;

    return SingleChildScrollView(
      child: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: Padding(
            padding: resolved,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: children,
            ),
          ),
        ),
      ),
    );
  }
}

/// Full-height column with expanded center (LocalSend `ColumnListView` receive layout).
class FocalColumnListView extends StatelessWidget {
  const FocalColumnListView({
    super.key,
    required this.children,
    this.padding = const EdgeInsets.all(30),
    this.maxWidth = 600,
  });

  final List<Widget> children;
  final EdgeInsets padding;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: Padding(
          padding: padding,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: children,
          ),
        ),
      ),
    );
  }
}
