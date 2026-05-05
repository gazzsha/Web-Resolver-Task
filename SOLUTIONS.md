# Web Resolver - Готовые решения задач

## Как использовать

1. Откройте http://localhost:3000/tasks
2. Выберите задачу
3. Нажмите "Solve Task"
4. Выберите язык (Java/Kotlin/Python)
5. Вставьте код решения
6. Нажмите "Submit Solution"

---

## Задача 1: Two Sum (Easy)
**ID:** `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

**Условие:** Даны массив чисел и целевое значение. Найдите два числа которые в сумме дают целевое значение.

### Java
```java
class Solution {
    public int[] twoSum(int[] nums, int target) {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        throw new IllegalArgumentException("No two sum solution");
    }
}
```

### Kotlin
```kotlin
class Solution {
    fun twoSum(nums: IntArray, target: Int): IntArray {
        val map = mutableMapOf<Int, Int>()
        for ((i, num) in nums.withIndex()) {
            val complement = target - num
            if (complement in map) {
                return intArrayOf(map[complement]!!, i)
            }
            map[num] = i
        }
        throw IllegalArgumentException("No two sum solution")
    }
}
```

### Python
```python
class Solution:
    def twoSum(self, nums: List[int], target: int) -> List[int]:
        num_map = {}
        for i, num in enumerate(nums):
            complement = target - num
            if complement in num_map:
                return [num_map[complement], i]
            num_map[num] = i
        raise ValueError("No two sum solution")
```

---

## Задача 2: Valid Parentheses (Easy)
**ID:** `b2c3d4e5-f6a7-8901-bcde-f12345678901`

**Условие:** Проверить правильность расстановки скобок.

### Java
```java
class Solution {
    public boolean isValid(String s) {
        java.util.Stack<Character> stack = new java.util.Stack<>();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
            } else {
                if (stack.isEmpty()) return false;
                char top = stack.pop();
                if ((c == ')' && top != '(') || 
                    (c == '}' && top != '{') || 
                    (c == ']' && top != '[')) {
                    return false;
                }
            }
        }
        return stack.isEmpty();
    }
}
```

### Kotlin
```kotlin
class Solution {
    fun isValid(s: String): Boolean {
        val stack = ArrayDeque<Char>()
        for (c in s) {
            if (c in "({[") stack.addLast(c)
            else {
                if (stack.isEmpty()) return false
                val top = stack.removeLast()
                if ((c == ')' && top != '(') || 
                    (c == '}' && top != '{') || 
                    (c == ']' && top != '[')) return false
            }
        }
        return stack.isEmpty()
    }
}
```

### Python
```python
class Solution:
    def isValid(self, s: str) -> bool:
        stack = []
        mapping = {')': '(', '}': '{', ']': '['}
        for char in s:
            if char in mapping:
                top_element = stack.pop() if stack else '#'
                if mapping[char] != top_element:
                    return False
            else:
                stack.append(char)
        return not stack
```

---

## Задача 3: Merge Two Sorted Lists (Easy)
**ID:** `c3d4e5f6-a7b8-9012-cdef-123456789012`

### Java
```java
class Solution {
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode dummy = new ListNode(0);
        ListNode current = dummy;
        while (list1 != null && list2 != null) {
            if (list1.val <= list2.val) {
                current.next = list1;
                list1 = list1.next;
            } else {
                current.next = list2;
                list2 = list2.next;
            }
            current = current.next;
        }
        current.next = (list1 != null) ? list1 : list2;
        return dummy.next;
    }
}
```

### Kotlin
```kotlin
class Solution {
    fun mergeTwoLists(l1: ListNode?, l2: ListNode?): ListNode? {
        val dummy = ListNode(0)
        var current = dummy
        var p1 = l1
        var p2 = l2
        while (p1 != null && p2 != null) {
            if (p1.`val` <= p2.`val`) {
                current.next = p1
                p1 = p1.next
            } else {
                current.next = p2
                p2 = p2.next
            }
            current = current.next!!
        }
        current.next = p1 ?: p2
        return dummy.next
    }
}
```

### Python
```python
class Solution:
    def mergeTwoLists(self, list1: Optional[ListNode], list2: Optional[ListNode]) -> Optional[ListNode]:
        dummy = ListNode(0)
        current = dummy
        while list1 and list2:
            if list1.val <= list2.val:
                current.next = list1
                list1 = list1.next
            else:
                current.next = list2
                list2 = list2.next
            current = current.next
        current.next = list1 or list2
        return dummy.next
```

---

## Задача 4: Valid Palindrome (Easy)
**ID:** `e5f6a7b8-c9d0-1234-ef01-345678901234`

### Java
```java
class Solution {
    public boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        while (left < right) {
            while (left < right && !Character.isLetterOrDigit(s.charAt(left))) left++;
            while (left < right && !Character.isLetterOrDigit(s.charAt(right))) right--;
            if (Character.toLowerCase(s.charAt(left)) != Character.toLowerCase(s.charAt(right))) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }
}
```

### Kotlin
```kotlin
class Solution {
    fun isPalindrome(s: String): Boolean {
        var left = 0
        var right = s.length - 1
        while (left < right) {
            while (left < right && !s[left].isLetterOrDigit()) left++
            while (left < right && !s[right].isLetterOrDigit()) right--
            if (s[left].lowercaseChar() != s[right].lowercaseChar()) return false
            left++
            right--
        }
        return true
    }
}
```

### Python
```python
class Solution:
    def isPalindrome(self, s: str) -> bool:
        left, right = 0, len(s) - 1
        while left < right:
            while left < right and not s[left].isalnum():
                left += 1
            while left < right and not s[right].isalnum():
                right -= 1
            if s[left].lower() != s[right].lower():
                return False
            left += 1
            right -= 1
        return True
```

---

## Задача 5: Median of Two Sorted Arrays (Hard)
**ID:** `c5d6e7f8-a9b0-1234-8901-345678901234`

### Java
```java
class Solution {
    public double findMedianSortedArrays(int[] nums1, int[] nums2) {
        if (nums1.length > nums2.length) {
            return findMedianSortedArrays(nums2, nums1);
        }
        int x = nums1.length;
        int y = nums2.length;
        int low = 0, high = x;
        while (low <= high) {
            int partitionX = (low + high) / 2;
            int partitionY = (x + y + 1) / 2 - partitionX;
            int maxLeftX = (partitionX == 0) ? Integer.MIN_VALUE : nums1[partitionX - 1];
            int minRightX = (partitionX == x) ? Integer.MAX_VALUE : nums1[partitionX];
            int maxLeftY = (partitionY == 0) ? Integer.MIN_VALUE : nums2[partitionY - 1];
            int minRightY = (partitionY == y) ? Integer.MAX_VALUE : nums2[partitionY];
            if (maxLeftX <= minRightY && maxLeftY <= minRightX) {
                if ((x + y) % 2 == 0) {
                    return (Math.max(maxLeftX, maxLeftY) + Math.min(minRightX, minRightY)) / 2.0;
                } else {
                    return Math.max(maxLeftX, maxLeftY);
                }
            } else if (maxLeftX > minRightY) {
                high = partitionX - 1;
            } else {
                low = partitionX + 1;
            }
        }
        throw new IllegalArgumentException();
    }
}
```

### Kotlin
```kotlin
class Solution {
    fun findMedianSortedArrays(nums1: IntArray, nums2: IntArray): Double {
        if (nums1.size > nums2.size) return findMedianSortedArrays(nums2, nums1)
        val x = nums1.size
        val y = nums2.size
        var low = 0
        var high = x
        while (low <= high) {
            val partitionX = (low + high) / 2
            val partitionY = (x + y + 1) / 2 - partitionX
            val maxLeftX = if (partitionX == 0) Int.MIN_VALUE else nums1[partitionX - 1]
            val minRightX = if (partitionX == x) Int.MAX_VALUE else nums1[partitionX]
            val maxLeftY = if (partitionY == 0) Int.MIN_VALUE else nums2[partitionY - 1]
            val minRightY = if (partitionY == y) Int.MAX_VALUE else nums2[partitionY]
            if (maxLeftX <= minRightY && maxLeftY <= minRightX) {
                return if ((x + y) % 2 == 0) {
                    (maxOf(maxLeftX, maxLeftY) + minOf(minRightX, minRightY)) / 2.0
                } else {
                    maxOf(maxLeftX, maxLeftY).toDouble()
                }
            } else if (maxLeftX > minRightY) {
                high = partitionX - 1
            } else {
                low = partitionX + 1
            }
        }
        throw IllegalArgumentException()
    }
}
```

### Python
```python
class Solution:
    def findMedianSortedArrays(self, nums1: List[int], nums2: List[int]) -> float:
        if len(nums1) > len(nums2):
            nums1, nums2 = nums2, nums1
        x, y = len(nums1), len(nums2)
        low, high = 0, x
        while low <= high:
            partitionX = (low + high) // 2
            partitionY = (x + y + 1) // 2 - partitionX
            maxLeftX = float('-inf') if partitionX == 0 else nums1[partitionX - 1]
            minRightX = float('inf') if partitionX == x else nums1[partitionX]
            maxLeftY = float('-inf') if partitionY == 0 else nums2[partitionY - 1]
            minRightY = float('inf') if partitionY == y else nums2[partitionY]
            if maxLeftX <= minRightY and maxLeftY <= minRightX:
                if (x + y) % 2 == 0:
                    return (max(maxLeftX, maxLeftY) + min(minRightX, minRightY)) / 2
                else:
                    return max(maxLeftX, maxLeftY)
            elif maxLeftX > minRightY:
                high = partitionX - 1
            else:
                low = partitionX + 1
        raise ValueError()
```

---

## Примечание

В данный момент система работает в демо-режиме с моковыми данными. Для полноценной работы требуется:
1. Настроить Worker Service для обработки задач из Kafka
2. Настроить Docker sandbox для безопасного выполнения кода
3. Добавить тестовые кейсы для каждой задачи
