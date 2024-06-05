import "fmt"

func add(a, b int) int {
 return a + b
}

func main(args []string) {

 num1, err1 := fmt.Atoi(args[1])
 num2, err2 := fmt.Atoi(args[2])

 result := add(num1, num2)
 fmt.Printf(result)
}