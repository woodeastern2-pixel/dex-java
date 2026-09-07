import 'package:flutter/material.dart';

/// AI VOC Intelligence Platform의 현대적 디자인 시스템
/// Material Design 3 기반 (2024 트렌드)
/// - Primary: Modern Blue (#2563EB) - 신뢰성, 기술성
/// - Secondary: Rich Violet (#7C3AED) - AI, 혁신성
/// - Tertiary: Emerald Green (#10B981) - 효율성, 해결
/// - Accent: Amber (#F59E0B) - 긴급도, 주의
class AppTheme {
  AppTheme._();

  // 주 색상 팔레트
  static const Color _primaryColor = Color(0xFF2563EB); // Modern Blue
  static const Color _secondaryColor = Color(0xFF7C3AED); // Rich Violet
  static const Color _tertiaryColor = Color(0xFF10B981); // Emerald Green
  static const Color _errorColor = Color(0xFFDC2626); // Deep Red
  static const Color _warningColor = Color(0xFFF59E0B); // Warm Amber
  static const Color _successColor = Color(0xFF059669); // True Green
  
  // 중립 색상
  static const Color _surface = Color(0xFFFAFAFA);
  static const Color _onSurface = Color(0xFF1F2937);
  static const Color _outline = Color(0xFFD1D5DB);
  static const Color _surfaceVariant = Color(0xFFF3F4F6);

  static ThemeData get lightTheme => ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    
    // 색상 스킴 정의
    colorScheme: ColorScheme(
      brightness: Brightness.light,
      primary: _primaryColor,
      onPrimary: Colors.white,
      primaryContainer: const Color(0xFFDEE9F8),
      onPrimaryContainer: const Color(0xFF001850),
      secondary: _secondaryColor,
      onSecondary: Colors.white,
      secondaryContainer: const Color(0xFFEDDEFF),
      onSecondaryContainer: const Color(0xFF2D1047),
      tertiary: _tertiaryColor,
      onTertiary: Colors.white,
      tertiaryContainer: const Color(0xFFD2F7ED),
      onTertiaryContainer: const Color(0xFF003828),
      error: _errorColor,
      onError: Colors.white,
      errorContainer: const Color(0xFFFEE2E2),
      onErrorContainer: const Color(0xFF5F0000),
      background: _surface,
      onBackground: _onSurface,
      surface: Colors.white,
      onSurface: _onSurface,
      surfaceVariant: _surfaceVariant,
      onSurfaceVariant: const Color(0xFF6B7280),
      outline: _outline,
      outlineVariant: const Color(0xFFE5E7EB),
      scrim: Colors.black,
      inverseSurface: const Color(0xFF1F2937),
      // inverseOnSurface: const Color(0xFFF9FAFB), // Not supported in this Flutter version
      inversePrimary: const Color(0xFF90CAF9),
    ),
    
    // 앱바 테마
    appBarTheme: const AppBarTheme(
      backgroundColor: _primaryColor,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: false,
      scrolledUnderElevation: 0,
      titleTextStyle: TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        color: Colors.white,
        letterSpacing: 0.15,
      ),
      iconTheme: IconThemeData(color: Colors.white),
    ),
    
    // 카드 테마
    cardTheme: CardThemeData(
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: _outline, width: 1),
      ),
      surfaceTintColor: _primaryColor,
      margin: const EdgeInsets.all(0),
    ),
    
    // 입력 필드 테마
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: _surfaceVariant,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: _outline, width: 1),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: _outline, width: 1),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: _primaryColor, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: _errorColor, width: 1),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: _errorColor, width: 2),
      ),
      labelStyle: const TextStyle(color: Color(0xFF6B7280), fontSize: 14),
      hintStyle: const TextStyle(color: Color(0xFF9CA3AF), fontSize: 14),
      prefixIconColor: const Color(0xFF6B7280),
      suffixIconColor: const Color(0xFF6B7280),
    ),
    
    // 버튼 테마
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: _primaryColor,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.1,
        ),
      ),
    ),
    
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: _primaryColor,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.1,
        ),
      ),
    ),
    
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: _primaryColor,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        side: const BorderSide(color: _primaryColor, width: 1),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.1,
        ),
      ),
    ),
    
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: _primaryColor,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        textStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.1,
        ),
      ),
    ),
    
    // 칩 테마
    chipTheme: ChipThemeData(
      backgroundColor: _surfaceVariant,
      selectedColor: _primaryColor,
      side: const BorderSide(color: _outline),
      labelStyle: const TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w500,
        color: _onSurface,
      ),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
    ),
    
    // 플로팅 액션 버튼 테마
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: _primaryColor,
      foregroundColor: Colors.white,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    
    // 다이얼로그 테마
    dialogTheme: DialogThemeData(
      backgroundColor: Colors.white,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      titleTextStyle: const TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        color: _onSurface,
        letterSpacing: 0.15,
      ),
      contentTextStyle: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: Color(0xFF6B7280),
        letterSpacing: 0.25,
      ),
    ),
    
    // 바텀 시트 테마
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      elevation: 8,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
    ),
    
    // 진행률 표시기 테마
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      linearMinHeight: 4,
      linearTrackColor: _outline,
      color: _primaryColor,
    ),
    
    // 구분선 테마
    dividerTheme: const DividerThemeData(
      color: _outline,
      thickness: 1,
      space: 16,
      indent: 0,
      endIndent: 0,
    ),
    
    // 탭 바 테마
    tabBarTheme: TabBarThemeData(
      indicatorSize: TabBarIndicatorSize.label,
      indicator: const BoxDecoration(
        border: Border(
          bottom: BorderSide(color: _primaryColor, width: 3),
        ),
      ),
      unselectedLabelColor: const Color(0xFF9CA3AF),
      labelColor: _primaryColor,
      labelStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
      unselectedLabelStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
    ),
    
    // 스캐폴드 백그라운드
    scaffoldBackgroundColor: _surface,
    
    // 텍스트 테마
    textTheme: const TextTheme(
      displayLarge: TextStyle(
        fontSize: 36,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
        color: _onSurface,
      ),
      displayMedium: TextStyle(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.25,
        color: _onSurface,
      ),
      displaySmall: TextStyle(
        fontSize: 24,
        fontWeight: FontWeight.w700,
        letterSpacing: 0,
        color: _onSurface,
      ),
      headlineLarge: TextStyle(
        fontSize: 22,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
        color: _onSurface,
      ),
      headlineMedium: TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.15,
        color: _onSurface,
      ),
      headlineSmall: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.15,
        color: _onSurface,
      ),
      titleLarge: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.15,
        color: _onSurface,
      ),
      titleMedium: TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w500,
        letterSpacing: 0.1,
        color: _onSurface,
      ),
      titleSmall: TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        letterSpacing: 0.1,
        color: _onSurface,
      ),
      bodyLarge: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.5,
        color: _onSurface,
      ),
      bodyMedium: TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.25,
        color: _onSurface,
      ),
      bodySmall: TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.4,
        color: _onSurface,
      ),
    ),
  );

  static ThemeData get darkTheme => ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    
    colorScheme: ColorScheme(
      brightness: Brightness.dark,
      primary: const Color(0xFF90CAF9),
      onPrimary: const Color(0xFF001850),
      primaryContainer: const Color(0xFF0D47A1),
      onPrimaryContainer: const Color(0xFFDEE9F8),
      secondary: const Color(0xFFD7C4FF),
      onSecondary: const Color(0xFF2D1047),
      secondaryContainer: const Color(0xFF4A235A),
      onSecondaryContainer: const Color(0xFFEDDEFF),
      tertiary: const Color(0xFF6EE7B7),
      onTertiary: const Color(0xFF003828),
      tertiaryContainer: const Color(0xFF00574B),
      onTertiaryContainer: const Color(0xFFD2F7ED),
      error: const Color(0xFFF87171),
      onError: const Color(0xFF5F0000),
      errorContainer: const Color(0xFF8B0000),
      onErrorContainer: const Color(0xFFFEE2E2),
      background: const Color(0xFF111827),
      onBackground: const Color(0xFFF9FAFB),
      surface: const Color(0xFF1F2937),
      onSurface: const Color(0xFFF3F4F6),
      surfaceVariant: const Color(0xFF374151),
      onSurfaceVariant: const Color(0xFFD1D5DB),
      outline: const Color(0xFF4B5563),
      outlineVariant: const Color(0xFF6B7280),
      scrim: Colors.black,
      inverseSurface: const Color(0xFFF9FAFB),
      // inverseOnSurface: const Color(0xFF111827), // Not supported in this Flutter version
      inversePrimary: const Color(0xFF2563EB),
    ),
    
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFF1F2937),
      foregroundColor: Colors.white,
      elevation: 0,
      scrolledUnderElevation: 0,
    ),
    
    cardTheme: CardThemeData(
      elevation: 0,
      color: const Color(0xFF1F2937),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: Color(0xFF374151), width: 1),
      ),
    ),
    
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: const Color(0xFF111827),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: Color(0xFF4B5563), width: 1),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: Color(0xFF4B5563), width: 1),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: Color(0xFF90CAF9), width: 2),
      ),
    ),
    
    scaffoldBackgroundColor: const Color(0xFF111827),
  );

  // ==================== 헬퍼 메서드 ====================
  
  /// 우선순위에 따른 색상 반환
  static Color priorityColor(String priority) {
    switch (priority.toUpperCase()) {
      case 'HIGH':
      case 'CRITICAL':
        return _errorColor;
      case 'MEDIUM':
        return _warningColor;
      case 'LOW':
        return _successColor;
      default:
        return Colors.grey;
    }
  }

  /// 상태에 따른 색상 반환
  static Color statusColor(String status) {
    switch (status.toUpperCase()) {
      case 'OPEN':
        return _primaryColor;
      case 'IN_PROGRESS':
        return _warningColor;
      case 'RESOLVED':
        return _successColor;
      case 'REJECTED':
        return _errorColor;
      case 'DRAFT':
        return const Color(0xFF9CA3AF);
      default:
        return Colors.grey;
    }
  }

  /// 긴급도에 따른 색상 반환
  static Color urgencyColor(String urgency) {
    switch (urgency.toUpperCase()) {
      case 'CRITICAL':
        return _errorColor;
      case 'HIGH':
        return _warningColor;
      case 'MEDIUM':
        return _secondaryColor;
      case 'LOW':
        return _tertiaryColor;
      default:
        return Colors.grey;
    }
  }

  /// AI 신뢰도에 따른 색상 (점수 0-1)
  static Color confidenceColor(double score) {
    if (score >= 0.8) return _successColor;
    if (score >= 0.6) return _warningColor;
    return _errorColor;
  }
}
