#include <iostream>

int add(int a, int b) {
    return a + b;
}

int main() {
    int num1, num2, result;
    std::cin >> num1 >> num2;
    result = add(num1, num2);
    std::cout  << result << std::endl;
    return 0;
}