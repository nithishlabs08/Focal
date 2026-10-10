import 'package:flutter/material.dart';

import '../../theme/focal_layout.dart';
import 'focal_page_header.dart';

/// Centered, max-width scroll column (LocalSend `ResponsiveListView` rhythm).
class FocalDesktopPage extends StatelessWidget {
  const FocalDesktopPage({
    super.key,
    required this.children,
    this.title,
    this.subtitle,
    this.centerHeader = false,
    this.topSpacing = 16,
  });

  final String? title;
  final String? subtitle;
  final bool centerHeader;
  final double topSpacing;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: FocalLayout.maxContentWidth),
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              FocalLayout.horizontalPadding,
              topSpacing,
              FocalLayout.horizontalPadding,
              FocalLayout.pageBottomPadding,
            ),
            children: [
              if (title != null)
                FocalPageHeader(
                  title: title!,
                  subtitle: subtitle,
                  center: centerHeader,
                ),
              ...children,
            ],
          ),
        ),
      ),
    );
  }
}
