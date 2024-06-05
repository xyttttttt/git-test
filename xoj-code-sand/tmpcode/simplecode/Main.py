import sys

def add_numbers(num1, num2):
    return num1 + num2

def main():
    # 检查传入参数的数量
    if len(sys.argv) != 3:
        print("Usage: python program.py <num1> <num2>")
        sys.exit(1)

    # 获取传入的两个参数
    num1 = float(sys.argv[1])
    num2 = float(sys.argv[2])

    # 计算两数之和
    result = add_numbers(num1, num2)
    print(int(result))

if __name__ == "__main__":
    main()