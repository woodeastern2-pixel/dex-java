import 'package:flutter/foundation.dart';

class AuthViewModel extends ChangeNotifier {
  bool _isLoggedIn = false;
  bool _isAdmin = false;
  String _userName = '';

  bool get isLoggedIn => _isLoggedIn;
  bool get isAdmin => _isAdmin;
  String get userName => _userName;

  void login(String name, {bool isAdmin = false}) {
    _userName = name;
    _isLoggedIn = true;
    _isAdmin = isAdmin;
    notifyListeners();
  }

  void logout() {
    _isLoggedIn = false;
    _isAdmin = false;
    _userName = '';
    notifyListeners();
  }

  void elevateToAdmin() {
    _isAdmin = true;
    notifyListeners();
  }

  void revokeAdmin() {
    _isAdmin = false;
    notifyListeners();
  }
}
