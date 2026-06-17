import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TwoSum {

    /**
     * Возвращает индексы двух элементов, сумма которых равна target.
     * Один проход: для каждого элемента проверяем, встречали ли мы уже
     * нужное дополнение (target - nums[i]).
     * Время: O(n), память: O(n).
     */
    public static int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>(); // значение -> индекс
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (seen.containsKey(complement)) {
                return new int[]{seen.get(complement), i};
            }
            seen.put(nums[i], i);
        }
        throw new IllegalArgumentException("Решение не найдено");
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String[] parts = scanner.nextLine().trim().split(",");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = Integer.parseInt(parts[i].trim());
        }

        int target = Integer.parseInt(scanner.nextLine().trim());

        int[] result = twoSum(nums, target);
        System.out.println(result[0] + " " + result[1]);
    }
}
