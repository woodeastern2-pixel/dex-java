import 'package:flutter/foundation.dart';
import '../../domain/repositories/voc_repository.dart';
import '../../domain/repositories/knowledge_base_repository.dart';
import '../../domain/services/ai_accuracy_service.dart';
import '../../domain/services/executive_dashboard_service.dart';

class DashboardViewModel extends ChangeNotifier {
  final VocRepository _vocRepository;
  final KnowledgeBaseRepository _kbRepository;
  final RoiCalculator _roiCalculator = DefaultRoiCalculator();
  final AiAccuracyService _aiAccuracyService = DefaultAiAccuracyService();

  Map<String, int> _vocByStatus = {};
  Map<String, int> _vocByCategory = {};
  List<Map<String, dynamic>> _monthlyStats = [];
  int _totalVocs = 0;
  int _resolvedVocs = 0;
  int _kbCount = 0;
  double _duplicateReductionRate = 0.0;
  double _aiUsageRate = 0.0;
  double _avgProcessMinutes = 0.0;
  List<Map<String, dynamic>> _assigneeStats = [];
  RoiResult? _roiResult;
  double _aiOverallAccuracy = 0.0;
  double _aiAnswerAdoptionRate = 0.0;
  List<String> _accuracyRecommendations = [];
  ExecutiveKpiDashboard? _executiveKpi;
  bool _isLoading = false;
  String? _error;

  DashboardViewModel(this._vocRepository, this._kbRepository) {
    loadDashboard();
  }

  Map<String, int> get vocByStatus => _vocByStatus;
  Map<String, int> get vocByCategory => _vocByCategory;
  List<Map<String, dynamic>> get monthlyStats => _monthlyStats;
  int get totalVocs => _totalVocs;
  int get resolvedVocs => _resolvedVocs;
  int get kbCount => _kbCount;
  double get duplicateReductionRate => _duplicateReductionRate;
  double get aiUsageRate => _aiUsageRate;
  double get avgProcessMinutes => _avgProcessMinutes;
  List<Map<String, dynamic>> get assigneeStats => _assigneeStats;
  RoiResult? get roiResult => _roiResult;
  double get aiOverallAccuracy => _aiOverallAccuracy;
  double get aiAnswerAdoptionRate => _aiAnswerAdoptionRate;
  List<String> get accuracyRecommendations => _accuracyRecommendations;
  ExecutiveKpiDashboard? get executiveKpi => _executiveKpi;
  bool get isLoading => _isLoading;
  String? get error => _error;

  double get resolutionRate =>
      _totalVocs == 0 ? 0.0 : _resolvedVocs / _totalVocs;

  int get openVocs => _vocByStatus['OPEN'] ?? 0;
  int get inProgressVocs => _vocByStatus['IN_PROGRESS'] ?? 0;

  Future<void> loadDashboard() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final results = await Future.wait([
        _vocRepository.getVocCountByStatus(),
        _vocRepository.getVocCountByCategory(),
        _vocRepository.getMonthlyStats(),
        _kbRepository.getTotalCount(),
        _vocRepository.getAdvancedMetrics(),
        _vocRepository.getTopAssigneeStats(topN: 5),
      ]);

      _vocByStatus = results[0] as Map<String, int>;
      _vocByCategory = results[1] as Map<String, int>;
      _monthlyStats = results[2] as List<Map<String, dynamic>>;
      _kbCount = results[3] as int;
      final adv = results[4] as Map<String, dynamic>;
      _duplicateReductionRate = (adv['duplicateReductionRate'] as num?)?.toDouble() ?? 0.0;
      _aiUsageRate = (adv['aiUsageRate'] as num?)?.toDouble() ?? 0.0;
      _avgProcessMinutes = (adv['avgProcessMinutes'] as num?)?.toDouble() ?? 0.0;
      _assigneeStats = (results[5] as List<Map<String, dynamic>>);

      _totalVocs = _vocByStatus.values.fold(0, (a, b) => a + b);
      _resolvedVocs = _vocByStatus['RESOLVED'] ?? 0;

      await _loadExecutiveAndRoiMetrics();
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> _loadExecutiveAndRoiMetrics() async {
    _executiveKpi = await _roiCalculator.getExecutiveDashboard();

    _roiResult = _roiCalculator.calculateRoi(
      RoiCalculatorInput(
        monthlyVocVolume: _totalVocs == 0 ? 100 : _totalVocs,
        avgHandleTimeHours: _avgProcessMinutes <= 0 ? 3.5 : _avgProcessMinutes / 60,
        hourlyLaborCost: 35.0,
        aiImplementationCost: 50000,
        monthlyAiMaintenanceCost: 2500,
        automationRate: _aiUsageRate <= 0 ? 0.65 : _aiUsageRate.clamp(0.0, 1.0),
        aiAccuracyRate: 0.9,
      ),
    );

    final accuracyStats = await _aiAccuracyService.getAccuracyStats();
    _aiOverallAccuracy = accuracyStats.overallAccuracy;
    _aiAnswerAdoptionRate = accuracyStats.answerAdoptionRate;
    _accuracyRecommendations =
        await _aiAccuracyService.getImprovementRecommendations();
  }
}
